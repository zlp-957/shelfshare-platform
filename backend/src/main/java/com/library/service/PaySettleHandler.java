package com.library.service;

import com.library.common.Constants;
import com.library.entity.PayOrder;
import com.library.event.PaySuccessEvent;
import com.library.mapper.PayOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付入账处理器（独立 Bean）。
 * <p>
 * 为什么单独抽一个类，而不是留在 PayServiceImpl 里：
 * <p>
 * 原来 {@code doPaid} 是 PayServiceImpl 的 protected 方法，由同类中的
 * {@code handleNotify} 和 {@code syncStatus} 通过 {@code this.doPaid()} 调用。
 * Spring 的 {@code @Transactional} 依赖代理，<b>同类自调用不经过代理，注解会被静默忽略</b>。
 * 回调路径因为外层 handleNotify 自带事务，问题被掩盖了；
 * 而定时查单补偿走的 {@code syncStatus} 没有事务，整条补偿链路实际是「无事务」的：
 * markPaid 成功、写流水失败时，会出现「钱到账了但账本上没这笔」。
 * <p>
 * 抽成独立 Bean 后，调用一定跨代理，事务才真正生效。
 * 同时这里用 REQUIRED：回调路径会加入外层事务；补偿路径会自己开一个。
 */
@Slf4j
@Service
public class PaySettleHandler {

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private PaymentFlowService paymentFlowService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 支付入账：幂等推进支付单 + 记流水 + 发业务事件。
     *
     * @return true 本次真正完成了入账；false 表示已被处理过（重复回调 / 查单撞车）
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean settle(PayOrder order, String tradeNo) {
        LocalDateTime now = LocalDateTime.now();
        // 幂等闸门在 SQL 里：status = 0 才允许推进
        int updated = payOrderMapper.markPaid(order.getOrderNo(), tradeNo, now);
        if (updated == 0) {
            log.info("支付单已处理，幂等返回 orderNo={}", order.getOrderNo());
            return false;
        }

        int flowType;
        String remark;
        if (order.getBizType() == Constants.PAY_BIZ_COMBINED) {
            flowType = Constants.FLOW_TYPE_COMBINED_IN;
            remark = "购物车合并支付";
        } else if (order.getBizType() == Constants.PAY_BIZ_DEPOSIT) {
            flowType = Constants.FLOW_TYPE_DEPOSIT_IN;
            remark = "借阅押金";
        } else {
            flowType = Constants.FLOW_TYPE_PURCHASE_IN;
            remark = "购买书款";
        }
        paymentFlowService.record(order.getUserId(), flowType, order.getBizId(),
                order.getOrderNo(), Constants.FLOW_DIRECTION_IN, order.getAmount(), remark);

        // 事件对象上的状态要同步成已支付，否则监听器读到的还是旧快照
        order.setStatus(Constants.PAY_STATUS_PAID);
        order.setTradeNo(tradeNo);
        order.setPayTime(now);
        eventPublisher.publishEvent(new PaySuccessEvent(order));
        log.info("支付入账成功 orderNo={} amount={} bizType={}",
                order.getOrderNo(), order.getAmount(), order.getBizType());
        return true;
    }
}
