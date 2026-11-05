package com.library.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志。
 * <p>
 * 资金流水（payment_flow）只回答「钱怎么动的」，回答不了「谁点的按钮」。
 * 管理员扣了读者 20 元赔偿、把账号禁用、把副本标成遗失，这些动作出问题时
 * 必须能查到操作人、时间、来源 IP 和当时的入参。
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人 id（未登录的操作为 null） */
    private Long userId;

    /** 操作人账号快照：账号后来被删也不影响追溯 */
    private String userAccount;

    /** 业务模块，如「借阅」「读者」「支付」 */
    private String module;

    /** 具体动作，如「借出核销」 */
    private String action;

    /** 入参摘要（JSON，超长截断） */
    private String detail;

    /** 来源 IP */
    private String ip;

    /** 是否成功：1 成功 0 失败 */
    private Integer success;

    /** 失败原因（异常信息，超长截断） */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
