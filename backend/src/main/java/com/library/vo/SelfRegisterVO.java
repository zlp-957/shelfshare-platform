package com.library.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 读者自助注册请求
 * <p>
 * 与管理员新增读者的 RegisterVO 区别：这里**不接收账号**。
 * 读者账号由系统按「R + 年份 + 4 位序号」统一编码，用户无法自选，
 * 避免重号、乱填、以及"账号里夹带个人信息"。
 */
@Data
public class SelfRegisterVO {

    @NotBlank(message = "请填写真实姓名")
    @Size(max = 20, message = "姓名长度不能超过 20 个字符")
    private String name;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    /**
     * 手机号必填，两个用途：一是到书/逾期通知的唯一联系方式，
     * 二是忘记密码时申请找回的身份核对依据（姓名+手机号须与账号绑定信息一致）。
     * 不填或乱填，等于两条后路都断了。
     */
    @NotBlank(message = "请填写手机号，用于到书通知和忘记密码时核对身份")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确，应为 11 位数字")
    private String phone;
}
