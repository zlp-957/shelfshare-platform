package com.library.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 一次对账的执行结果
 */
@Data
public class ReconcileResultVO {

    /** 账期 */
    private LocalDate billDate;

    /**
     * 是否为模拟对账。
     * <p>
     * 模拟支付模式下渠道侧没有任何交易，拿不到真实账单，
     * 此时用本地数据构造「渠道侧」，只为把比对、落库、页面展示这条链路跑通。
     * 要真正验证对账能力，必须关掉 mock 打真实沙箱。
     */
    private Boolean simulated;

    /** 本地已支付单数 */
    private Integer localCount;

    /** 渠道账单笔数 */
    private Integer channelCount;

    /** 发现的差异笔数 */
    private Integer diffCount;

    /** 说明 */
    private String message;
}
