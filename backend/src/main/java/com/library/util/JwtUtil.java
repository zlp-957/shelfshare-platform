package com.library.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * JWT 工具类
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${library.jwt.secret:}")
    private String secretFromConfig;

    @Value("${library.jwt.expire-hours}")
    private long expireHours;

    private Key key;

    @PostConstruct
    public void init() {
        // 优先使用环境变量 / 启动参数中的密钥，避免密钥硬编码进配置文件随代码泄露
        String secret = resolveSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT 密钥长度不足 32 字节，请通过环境变量 LIBRARY_JWT_SECRET 配置");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 密钥解析顺序：环境变量 LIBRARY_JWT_SECRET > 启动参数 -DLIBRARY_JWT_SECRET > 配置文件
     */
    private String resolveSecret() {
        String fromEnv = System.getenv("LIBRARY_JWT_SECRET");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        String fromProp = System.getProperty("LIBRARY_JWT_SECRET");
        if (fromProp != null && !fromProp.trim().isEmpty()) {
            return fromProp.trim();
        }
        if (secretFromConfig != null && !secretFromConfig.trim().isEmpty()) {
            log.warn("当前使用配置文件中的 JWT 密钥，生产环境请改用环境变量 LIBRARY_JWT_SECRET 注入");
            return secretFromConfig.trim();
        }
        return null;
    }

    /**
     * 生成 token
     *
     * @param tokenVersion 令牌版本，写入 claim；改密后自增即可让该用户所有旧 token 失效
     */
    public String generateToken(Long userId, String account, Integer role, Integer tokenVersion) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 3600 * 1000);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("account", account)
                .claim("role", role)
                .claim("tv", tokenVersion == null ? 0 : tokenVersion)
                .setIssuedAt(now)
                .setExpiration(expire)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 token，返回 Claims；失败返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }
}
