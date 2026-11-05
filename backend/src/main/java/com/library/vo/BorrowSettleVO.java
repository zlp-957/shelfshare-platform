package com.library.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 借出 / 归还核销结果
 */
@Data
public class BorrowSettleVO {

    private Long borrowId;

    private String bookName;

    private String barcode;

    private String userAccount;

    private String userName;

    /** 押金总额 */
    private BigDecimal depositAmount;

    /** 本次扣除（逾期费 + 破损赔偿） */
    private BigDecimal deductAmount;

    /** 本次退还 */
    private BigDecimal refundAmount;

    /** 逾期天数，0 表示未逾期 */
    private Long overdueDays;

    /** 逾期费明细 */
    private BigDecimal overdueFee;

    /** 破损赔偿明细 */
    private BigDecimal damageFee;

    /** 操作后的借阅单状态 */
    private Integer status;

    private String statusText;
}
