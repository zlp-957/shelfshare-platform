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
 * 资金流水表
 * <p>
 * 账本语义：只追加，不修改，不删除（因此没有 is_delete 字段）。
 * 2.0 没有账户余额，本表的作用是让每一笔押金的进出都有据可查，
 * 用于对账、纠纷举证、财务核对。
 */
@Data
@TableName("payment_flow")
public class PaymentFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 类型：1押金收入 2书款收入 3押金退还 4逾期扣款 5破损扣款 6买断扣款 */
    private Integer bizType;

    /** 关联业务id */
    private Long bizId;

    /** 关联单号 */
    private String bizNo;

    /** 方向：1收入 2支出 */
    private Integer direction;

    /** 金额（恒为正数，方向由 direction 表达） */
    private BigDecimal amount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
