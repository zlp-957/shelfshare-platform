package com.library.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 密码重置申请
 * <p>
 * 流程：读者提交（姓名+手机号）→ 管理员电话核实 → 发放一次性重置码 → 读者自行设置新密码。
 * 管理员全程不接触用户的新密码，只负责放行。
 */
@Data
@TableName("password_reset_request")
public class PasswordResetRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 匹配到的读者id */
    private Long userId;

    /** 读者账号（冗余，便于列表展示） */
    private String account;

    /** 申请时填写的姓名 */
    private String applyName;

    /** 申请时填写的手机号 */
    private String applyPhone;

    /** 状态：0待处理 1已发重置码 2已完成 3已拒绝/过期 */
    private Integer status;

    /** 一次性重置码 */
    private String resetCode;

    /** 重置码过期时间 */
    private LocalDateTime codeExpire;

    /** 处理的管理员id */
    private Long handledBy;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 处理备注 */
    private String remark;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
