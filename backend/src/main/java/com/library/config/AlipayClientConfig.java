package com.library.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 支付宝客户端配置
 */
@Slf4j
@Configuration
public class AlipayClientConfig {

    @Bean
    public AlipayClient alipayClient(AlipayProperties props) throws Exception {
        String privateKey = loadPrivateKey(props.getAppPrivateKeyPath());
        log.info("初始化支付宝客户端 appId={} gateway={} signType={}",
                props.getAppId(), props.getGatewayUrl(), props.getSignType());
        return new DefaultAlipayClient(
                props.getGatewayUrl(),
                props.getAppId(),
                privateKey,
                props.getFormat(),
                props.getCharset(),
                props.getAlipayPublicKey(),
                props.getSignType());
    }

    /**
     * 读取私钥并转成 SDK 要求的格式。
     * <p>
     * 支付宝 SDK 只接受纯 base64 字符串，不能带 PEM 头尾和换行，
     * 所以这里把 -----BEGIN PRIVATE KEY----- 这类标记全部剥掉。
     */
    private String loadPrivateKey(String path) throws Exception {
        String raw;
        if (path.startsWith("classpath:")) {
            ClassPathResource resource = new ClassPathResource(path.substring("classpath:".length()));
            try (InputStream in = resource.getInputStream()) {
                raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            }
        } else {
            raw = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        }
        String key = raw
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        if (key.isEmpty()) {
            throw new IllegalStateException("应用私钥为空，请检查 " + path);
        }
        return key;
    }
}
