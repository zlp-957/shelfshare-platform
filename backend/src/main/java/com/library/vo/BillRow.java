package com.library.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 渠道账单中的一行（只保留对账需要的字段）
 */
@Data
public class BillRow {

    /** 支付宝交易号 */
    private String tradeNo;

    /** 商户订单号（对应本地 pay_order.order_no） */
    private String outTradeNo;

    /** 订单金额 */
    private BigDecimal amount;
}
