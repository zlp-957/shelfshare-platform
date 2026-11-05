package com.library.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.config.AlipayProperties;
import com.library.entity.PayOrder;
import com.library.mapper.PayOrderMapper;
import com.library.service.PayService;
import com.library.service.PaySettleHandler;
import com.library.util.OrderNoGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付服务实现（支付宝）
 */
@Slf4j
@Service
public class PayServiceImpl implements PayService {

    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    private static final String TRADE_FINISHED = "TRADE_FINISHED";

    @Autowired
    private PayOrderMapper payOrderMapper;
    /** 入账逻辑独立成 Bean：同类自调用会绕过 Spring 代理，@Transactional 失效 */
    @Autowired
    private PaySettleHandler paySettleHandler;
    @Autowired
    private OrderNoGenerator orderNoGenerator;
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private AlipayProperties props;

    @Override
    @Transactional
    public PayOrder createOrder(Long userId, int bizType, Long bizId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("支付金额必须大于 0");
        }
        PayOrder order = new PayOrder();
        order.setOrderNo(orderNoGenerator.next("PO"));
        order.setUserId(userId);
        order.setBizType(bizType);
        order.setBizId(bizId);
        order.setAmount(amount);
        order.setRefundAmount(BigDecimal.ZERO);
        order.setStatus(Constants.PAY_STATUS_UNPAID);
        order.setChannel(Constants.PAY_CHANNEL_ALIPAY);
        order.setExpireTime(LocalDateTime.now().plusMinutes(props.getOrderExpireMinutes()));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        payOrderMapper.insert(order);
        return order;
    }

    @Override
    public String buildPayForm(String orderNo) {
        PayOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("支付单不存在");
        }
        if (order.getStatus() != Constants.PAY_STATUS_UNPAID) {
            throw new BusinessException("该支付单已处理，无需重复支付");
        }
        if (props.isMockEnabled()) {
            // 模拟模式不生成收银台表单，前端看到空表单会展示「模拟支付」入口
            return "";
        }
        String subject = order.getBizType() == Constants.PAY_BIZ_COMBINED ? "图书借阅与购买"
                : (order.getBizType() == Constants.PAY_BIZ_DEPOSIT ? "图书借阅押金" : "图书购买");

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(props.getNotifyUrl());
        request.setReturnUrl(props.getReturnUrl());
        // 手动拼 JSON，避免额外引入序列化依赖
        request.setBizContent("{"
                + "\"out_trade_no\":\"" + order.getOrderNo() + "\","
                + "\"total_amount\":\"" + order.getAmount().toPlainString() + "\","
                + "\"subject\":\"" + subject + "\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\","
                + "\"timeout_express\":\"" + props.getOrderExpireMinutes() + "m\""
                + "}");
        try {
            return alipayClient.pageExecute(request, "POST").getBody();
        } catch (AlipayApiException e) {
            log.error("构建支付宝收银台表单失败 orderNo={}", orderNo, e);
            throw new BusinessException("发起支付失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public void handleNotify(Map<String, String> params) {
        // 1. 验签：防伪造回调，这一步绝不能省
        boolean signOk;
        try {
            signOk = AlipaySignature.rsaCheckV1(
                    params, props.getAlipayPublicKey(), props.getCharset(), props.getSignType());
        } catch (AlipayApiException e) {
            log.error("支付宝回调验签异常", e);
            throw new BusinessException("回调验签异常");
        }
        if (!signOk) {
            log.warn("支付宝回调验签失败 out_trade_no={}", params.get("out_trade_no"));
            throw new BusinessException("回调验签失败");
        }

        String tradeStatus = params.get("trade_status");
        if (!TRADE_SUCCESS.equals(tradeStatus) && !TRADE_FINISHED.equals(tradeStatus)) {
            log.info("支付宝回调非成功状态，忽略 trade_status={}", tradeStatus);
            return;
        }

        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String totalAmount = params.get("total_amount");

        PayOrder order = getByOrderNo(orderNo);
        if (order == null) {
            // 本地没有这笔单，返回成功避免支付宝反复重推（已记录日志供排查）
            log.error("回调对应的支付单不存在 orderNo={}", orderNo);
            return;
        }

        // 2. 金额比对：以本地订单金额为准
        BigDecimal notifyAmount;
        try {
            notifyAmount = new BigDecimal(totalAmount);
        } catch (Exception e) {
            throw new BusinessException("回调金额格式错误");
        }
        if (notifyAmount.compareTo(order.getAmount()) != 0) {
            log.error("回调金额与订单不一致 orderNo={} 订单={} 回调={}",
                    orderNo, order.getAmount(), notifyAmount);
            throw new BusinessException("回调金额与订单金额不一致");
        }

        doPaid(order, tradeNo);
    }

    /**
     * 入账（回调与主动查单共用的唯一入口）
     * <p>
     * 真正的实现在 {@link PaySettleHandler}：事务注解必须挂在被跨类调用的 Bean 上才生效，
     * 放在本类再由 this 调用等于没加。幂等仍由 SQL 条件更新保证。
     */
    private void doPaid(PayOrder order, String tradeNo) {
        paySettleHandler.settle(order, tradeNo);
    }

    @Override
    public boolean syncStatus(String orderNo) {
        PayOrder order = getByOrderNo(orderNo);
        if (order == null) {
            return false;
        }
        if (order.getStatus() != Constants.PAY_STATUS_UNPAID) {
            return false;
        }
        // 模拟支付单只存在于本地，去支付宝查必然查不到，白白多耗一次网络往返
        if (props.isMockEnabled()) {
            return false;
        }
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent("{\"out_trade_no\":\"" + orderNo + "\"}");
        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if (!response.isSuccess()) {
                return false;
            }
            String status = response.getTradeStatus();
            if (TRADE_SUCCESS.equals(status) || TRADE_FINISHED.equals(status)) {
                BigDecimal channelAmount = new BigDecimal(response.getTotalAmount());
                if (channelAmount.compareTo(order.getAmount()) != 0) {
                    log.error("查单金额与本地不一致 orderNo={} 本地={} 渠道={}",
                            orderNo, order.getAmount(), channelAmount);
                    return false;
                }
                doPaid(order, response.getTradeNo());
                return true;
            }
            if ("TRADE_CLOSED".equals(status)) {
                payOrderMapper.closeOrder(orderNo);
            }
            return false;
        } catch (AlipayApiException e) {
            log.error("查单失败 orderNo={}", orderNo, e);
            return false;
        }
    }

    @Override
    public void refund(String orderNo, BigDecimal amount, String reason, String requestNo) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        PayOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("支付单不存在");
        }
        if (order.getStatus() != Constants.PAY_STATUS_PAID
                && order.getStatus() != Constants.PAY_STATUS_PARTIAL_REFUNDED) {
            throw new BusinessException("该支付单当前状态不可退款");
        }
        // 本地先校验累计退款不超原额，避免白跑一次支付宝接口
        BigDecimal alreadyRefunded = order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount();
        if (alreadyRefunded.add(amount).compareTo(order.getAmount()) > 0) {
            throw new BusinessException("退款金额超出可退范围");
        }

        if (props.isMockEnabled()) {
            log.info("[MOCK] 模拟退款 orderNo={} amount={} reason={}", orderNo, amount, reason);
            int mockUpdated = payOrderMapper.addRefund(orderNo, amount);
            if (mockUpdated == 0) {
                throw new BusinessException("退款金额超出可退范围");
            }
            return;
        }

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizContent("{"
                + "\"out_trade_no\":\"" + orderNo + "\","
                + "\"refund_amount\":\"" + amount.toPlainString() + "\","
                + "\"out_request_no\":\"" + requestNo + "\","
                + "\"refund_reason\":\"" + (reason == null ? "退款" : reason) + "\""
                + "}");
        try {
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            if (!response.isSuccess()) {
                log.error("支付宝退款失败 orderNo={} code={} msg={}",
                        orderNo, response.getSubCode(), response.getSubMsg());
                throw new BusinessException("退款失败：" + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            log.error("调用支付宝退款接口异常 orderNo={}", orderNo, e);
            throw new BusinessException("退款请求失败，请稍后重试");
        }

        // 渠道已退成功，再更新本地累计金额。若这里失败说明并发超退，必须告警人工核对。
        int updated = payOrderMapper.addRefund(orderNo, amount);
        if (updated == 0) {
            log.error("【需人工核对】渠道已退款但本地累计更新失败 orderNo={} amount={}", orderNo, amount);
            throw new BusinessException("退款状态同步异常，请联系管理员");
        }
    }

    @Override
    public void closeOrder(String orderNo) {
        PayOrder order = getByOrderNo(orderNo);
        if (order == null || order.getStatus() != Constants.PAY_STATUS_UNPAID) {
            return;
        }
        // 模拟单在支付宝侧不存在，调远程关单只会在日志里刷 ACQ.TRADE_NOT_EXIST，
        // 还要白等一次网关往返（实测 0.7~1.2s），所以本地直接关
        if (props.isMockEnabled()) {
            log.info("[MOCK] 模拟关单 orderNo={}", orderNo);
            payOrderMapper.closeOrder(orderNo);
            return;
        }
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizContent("{\"out_trade_no\":\"" + orderNo + "\"}");
        try {
            AlipayTradeCloseResponse response = alipayClient.execute(request);
            if (!response.isSuccess()) {
                log.warn("关闭支付宝订单失败 orderNo={} msg={}", orderNo, response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            log.warn("关闭支付宝订单异常 orderNo={}", orderNo, e);
        }
        payOrderMapper.closeOrder(orderNo);
    }

    @Override
    public PayOrder getByOrderNo(String orderNo) {
        return payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOrderNo, orderNo));
    }

    @Override
    @Transactional
    public void mockPay(String orderNo) {
        if (!props.isMockEnabled()) {
            throw new BusinessException("模拟支付未开启，请检查配置 library.pay.alipay.mock-enabled");
        }
        PayOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("支付单不存在");
        }
        if (order.getStatus() != Constants.PAY_STATUS_UNPAID) {
            throw new BusinessException("该支付单已处理，无需重复支付");
        }
        // 与真实回调走完全相同的入账路径，避免「模拟能过、真机不行」
        doPaid(order, "MOCK" + System.currentTimeMillis());
    }

    @Override
    public boolean isMockMode() {
        return props.isMockEnabled();
    }

}
