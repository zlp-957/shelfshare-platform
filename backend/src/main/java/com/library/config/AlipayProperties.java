package com.library.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝配置
 * <p>
 * 沙箱与正式的差异只有 5 个值：appId、gatewayUrl、两把密钥、notifyUrl。
 * 切换环境时只改 application.yml，业务代码零改动。
 */
@Data
@Component
@ConfigurationProperties(prefix = "library.pay.alipay")
public class AlipayProperties {

    /** 是否启用真实支付宝渠道（false 时走模拟） */
    private boolean enabled = true;

    /** 应用APPID（沙箱与正式是两套，不能混用） */
    private String appId;

    /** 网关地址：沙箱 openapi-sandbox.dl.alipaydev.com / 正式 openapi.alipay.com */
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";

    /** 异步通知地址（必须公网可达） */
    private String notifyUrl;

    /** 同步跳转地址（仅用于展示，不能作为支付成功依据） */
    private String returnUrl;

    /** 签名类型，固定 RSA2（即 SHA256withRSA） */
    private String signType = "RSA2";

    private String charset = "UTF-8";

    private String format = "json";

    /** 应用私钥文件位置，支持 classpath: 与绝对路径 */
    private String appPrivateKeyPath = "classpath:certs/app_private_key.txt";

    /** 支付宝公钥（用于验签回调，注意不是应用公钥） */
    private String alipayPublicKey;

    /** 支付单有效分钟数 */
    private int orderExpireMinutes = 15;

    /**
     * 是否启用模拟支付。
     * <p>
     * 开启后不调用支付宝网关：下单返回空表单，退款直接改本地状态，
     * 由 /pay/mock/pay/{orderNo} 手动触发「支付成功」。
     * 用于本地开发与自动化测试，生产环境必须为 false。
     */
    private boolean mockEnabled = false;
}
