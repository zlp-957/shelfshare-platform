package com.library.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则同意记录
 * <p>
 * 弹窗本身不值钱，留痕才值钱。发生押金纠纷时，能证明
 * 「谁、在什么时间、同意的是哪个版本的规则」。
 * 协议改版后升 version，老用户需重新确认，否则新条款对其无效。
 */
@Data
@TableName("user_agreement")
public class UserAgreement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 类型：1借阅规则 2购买须知 3用户协议 */
    private Integer agreementType;

    /** 协议版本号，如 2.0 */
    private String version;

    private LocalDateTime agreedTime;

    /** 客户端IP（留痕） */
    private String clientIp;
}
