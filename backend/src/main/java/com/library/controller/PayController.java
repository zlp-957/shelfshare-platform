package com.library.controller;

import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.common.Result;
import com.library.entity.PayOrder;
import com.library.service.PayService;
import com.library.util.UserContext;
import com.library.vo.PayVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付接口
 */
@Slf4j
@RestController
@RequestMapping("/pay")
public class PayController {

    @Autowired
    private PayService payService;

    /**
     * 重新获取收银台表单（用户关掉页面后再次支付）
     */
    @GetMapping("/form/{orderNo}")
    public Result<PayVO> form(@PathVariable String orderNo) {
        PayOrder order = requireMyOrder(orderNo);
        PayVO vo = new PayVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setExpireTime(order.getExpireTime());
        if (order.getStatus() == Constants.PAY_STATUS_UNPAID) {
            vo.setPayForm(payService.buildPayForm(orderNo));
        }
        return Result.success(vo);
    }

    /**
     * 查询支付状态（前端轮询）
     * <p>
     * 未支付时顺带向渠道查一次，回调丢失也能在这里补上。
     */
    @GetMapping("/status/{orderNo}")
    public Result<Integer> status(@PathVariable String orderNo) {
        PayOrder order = requireMyOrder(orderNo);
        if (order.getStatus() == Constants.PAY_STATUS_UNPAID) {
            payService.syncStatus(orderNo);
            order = payService.getByOrderNo(orderNo);
        }
        return Result.success(order.getStatus());
    }

    /**
     * 支付宝异步回调
     * <p>
     * 注意三件事：
     * 1. 本路径必须在 WebConfig 里放行，渠道服务器不会带我们系统的 token
     * 2. 必须返回纯文本 success，返回 JSON 会被判为失败并重推 8 次
     * 3. 处理失败返回 failure 让渠道重试，但业务性失败（如金额不符）应记录告警
     */
    @PostMapping("/notify/alipay")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        log.info("收到支付宝回调 out_trade_no={} trade_status={}",
                params.get("out_trade_no"), params.get("trade_status"));
        try {
            payService.handleNotify(params);
            return "success";
        } catch (Exception e) {
            log.error("处理支付宝回调失败 out_trade_no={}", params.get("out_trade_no"), e);
            return "failure";
        }
    }

    /**
     * 模拟支付成功（仅 mock 模式可用）
     * <p>
     * 本地没有沙箱网络、或写自动化测试时用它代替真实收银台。
     * 走的是与真实回调完全相同的入账逻辑。
     */
    @PostMapping("/mock/pay/{orderNo}")
    public Result<?> mockPay(@PathVariable String orderNo) {
        requireMyOrder(orderNo);
        payService.mockPay(orderNo);
        return Result.success("模拟支付成功", null);
    }

    private PayOrder requireMyOrder(String orderNo) {
        PayOrder order = payService.getByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("支付单不存在");
        }
        Long userId = UserContext.getUserId();
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new BusinessException("无权查看该支付单");
        }
        return order;
    }
}
