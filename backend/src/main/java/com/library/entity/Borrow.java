package com.library.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 借阅记录表
 * <p>
 * 2.0 语义变化：
 * - 指向副本（item_id）而非书目，扫码核销才成立
 * - 借期从 verify_time（管理员核销时刻）起算，而非下单时刻
 * - 押金制：deposit_amount 收进来，deduct_amount 扣掉，refund_amount 退回
 * 不变式：deduct_amount + refund_amount <= deposit_amount
 */
@Data
@TableName("borrow")
public class Borrow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 图书id（书目） */
    private Long bookId;

    /** 副本id（核销时确定） */
    private Long itemId;

    /** 下单支付时间 */
    private LocalDateTime borrowTime;

    /** 借出核销时间（借期起点） */
    private LocalDateTime verifyTime;

    /** 应还时间 */
    private LocalDateTime deadlineTime;

    /** 押金金额 */
    private BigDecimal depositAmount;

    /** 已扣押金（逾期 + 破损） */
    private BigDecimal deductAmount;

    /** 已退押金 */
    private BigDecimal refundAmount;

    /** 押金退款状态：0无需退款 1已退成功 2失败待重试 */
    private Integer refundStatus;

    /**
     * 退款失败时刻（滞留计时起点）。
     * <p>
     * 为什么不复用 update_time：这行数据后续任何无关更新都会把 update_time 推后，
     * 滞留该报的时间被悄悄延后。这个字段只在「标记退款失败」那一处写入，
     * 之后不再被触碰，用它算「等了多久」才准。
     */
    private LocalDateTime refundFailTime;

    /** 押金支付单号 */
    private String payOrderNo;

    /** 取书码（6 位数字，支付成功后生成，用于到馆核销定位借阅单） */
    private String pickupCode;

    /** 实际归还时间 */
    private LocalDateTime returnTime;

    /** 状态：0待取书 1借阅中 2已归还 3已买断 4已取消 */
    private Integer status;

    /** 是否逾期：0否 1是 */
    private Integer isOverdue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
