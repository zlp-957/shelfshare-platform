package com.library.event;

import com.library.entity.PayOrder;
import org.springframework.context.ApplicationEvent;

/**
 * 支付成功事件
 * <p>
 * 用事件而不是直接调用，是为了避免「PayService 依赖 BorrowService、
 * BorrowService 又依赖 PayService」的循环依赖。
 * 业务方监听本事件推进自己的状态，与支付模块解耦。
 */
public class PaySuccessEvent extends ApplicationEvent {

    private final PayOrder payOrder;

    public PaySuccessEvent(PayOrder payOrder) {
        super(payOrder);
        this.payOrder = payOrder;
    }

    public PayOrder getPayOrder() {
        return payOrder;
    }
}
