package com.library.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单表
 * <p>
 * 与业务单（borrow / purchase_order）分离：业务单管「做什么」，支付单管「钱怎么走」。
 * order_no 是业务幂等键，回调重复推送时靠它去重。
 */
@Data
@TableName("pay_order")
public class PayOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支付单号（业务幂等键，唯一索引） */
    private String orderNo;

    private Long userId;

    /** 业务类型：1借阅押金 2购买书款 */
    private Integer bizType;

    /** 关联业务id（borrow.id 或 purchase_order.id） */
    private Long bizId;

    /** 应付金额 */
    private BigDecimal amount;

    /** 已退金额（累计，用于校验累计退款不超过原交易额） */
    private BigDecimal refundAmount;

    /** 状态：0待支付 1已支付 2已关闭 3已全额退款 4部分退款 */
    private Integer status;

    /** 渠道：1支付宝 3模拟 */
    private Integer channel;

    /** 渠道交易号（支付宝 trade_no） */
    private String tradeNo;

    private LocalDateTime payTime;

    /** 订单失效时间 */
    private LocalDateTime expireTime;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
