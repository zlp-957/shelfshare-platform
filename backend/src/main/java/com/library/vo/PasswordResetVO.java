package com.library.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 密码重置申请视图对象（管理端列表用）
 */
@Data
public class PasswordResetVO {

    private Long id;
    private Long userId;
    private String account;
    private String applyName;
    private String applyPhone;

    private Integer status;
    private String statusText;

    /** 重置码（只在生成的那一刻返回给管理员，列表里不回显，避免泄露 */
    private String resetCode;
    private LocalDateTime codeExpire;

    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}
