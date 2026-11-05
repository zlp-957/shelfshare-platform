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
 * 用户表
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账号：读者 R+8位数字 / 管理员 A+6位数字 */
    private String account;

    private String password;

    private String name;

    /** 角色：0读者 1管理员 */
    private Integer role;

    /** 状态：0正常 1禁用 */
    private Integer status;

    /**
     * 令牌版本。修改密码时自增，签发时写入 JWT，
     * 拦截器比对不一致即判定旧令牌失效——JWT 无状态，这是最轻量的吊销手段
     */
    private Integer tokenVersion;

    /** 头像地址 */
    private String avatar;

    /** 手机号（仅采集，不验证） */
    private String phone;

    /**
     * 取书码（6 位数字，30 分钟有效）。
     * <p>
     * 一个用户一个码，覆盖他全部待取的借阅与购买——
     * 到馆报这一个码，工作人员就能看到这个人要拿哪些书。
     * 核销后作废，防止朋友看到码后冒领。
     */
    private String pickupCode;

    /** 取书码过期时间 */
    private LocalDateTime pickupCodeExpire;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
