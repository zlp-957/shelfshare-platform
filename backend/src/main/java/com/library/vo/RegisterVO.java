package com.library.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 读者注册请求
 */
@Data
public class RegisterVO {

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^R\\d{8}$", message = "读者账号格式不正确，应为 R + 8位数字，如 R20260001")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    @NotBlank(message = "请填写真实姓名")
    @Size(max = 20, message = "姓名长度不能超过 20 个字符")
    private String name;

    /**
     * 手机号（可选）。管理员新增读者时可一并填写，方便出问题时联系；
     * 读者自助注册时通常不填，后续在个人中心补。
     * 格式校验放在 Service，和「修改手机号」「编辑读者」共用同一套规则。
     */
    private String phone;
}
