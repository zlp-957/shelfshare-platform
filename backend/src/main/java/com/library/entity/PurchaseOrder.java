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
 * 购买订单表
 */
@Data
@TableName("purchase_order")
public class PurchaseOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long bookId;

    /** 副本id（购买锁定的是具体某一册） */
    private Long itemId;

    /** 订单编号 */
    private String orderNo;

    /** 支付单号 */
    private String payOrderNo;

    /** 购买类型：0直接购买 1逾期转购买（2.0 已废弃） */
    private Integer buyType;

    /** 成交价格 */
    private BigDecimal price;

    /** 状态：0待支付 1已支付 2已取消 */
    private Integer status;

    /**
     * 取书状态：0待取书 1已取书（仅 status=1 时有意义）。
     * <p>
     * 付款成功不直接算交易完成——书还在馆里等用户来拿。
     * 待取书阶段用户可取消并全额退款；核销取书后交易结束，不再支持退款。
     */
    private Integer pickupStatus;

    /** 取书核销时间 */
    private LocalDateTime pickupTime;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
