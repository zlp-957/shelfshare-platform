package com.library.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 借阅记录视图对象
 */
@Data
public class BorrowVO {

    private Long id;
    private Long userId;
    private String userAccount;
    private String userName;

    private Long bookId;
    private String bookName;
    private String bookCover;

    /** 副本id */
    private Long itemId;
    /** 副本条码（核销后才有） */
    private String barcode;

    /** 下单支付时间 */
    private LocalDateTime borrowTime;
    /** 借出核销时间（借期起点） */
    private LocalDateTime verifyTime;
    /** 应还时间 */
    private LocalDateTime deadlineTime;
    /** 实际归还时间 */
    private LocalDateTime returnTime;

    /** 押金 */
    private BigDecimal depositAmount;
    /** 已扣押金 */
    private BigDecimal deductAmount;
    /** 已退押金 */
    private BigDecimal refundAmount;
    /** 押金退款状态：0无需退款 1已退成功 2失败待重试（前端据此区分「已退还」与「退款处理中」） */
    private Integer refundStatus;

    /** 押金支付单号 */
    private String payOrderNo;
    /** 支付单状态：0待支付 1已支付 2已关闭 3已全额退款 4部分退款 */
    private Integer payStatus;

    /** 取书码（6 位数字，到馆核销时出示） */
    private String pickupCode;

    /** 状态：0待取书 1借阅中 2已归还 3已买断 4已取消 */
    private Integer status;
    /** 状态文字 */
    private String statusText;

    private Integer isOverdue;

    /** 距离应还还剩多少天（负数表示已逾期天数），未开始借期时为 null */
    private Long remainDays;
}
