package com.library.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析。
 * <p>
 * 为什么不直接读 {@code X-Forwarded-For}：这个头是客户端可以随意伪造的。
 * 直连部署时如果无条件取它，任何限流、签署留痕都会变成摆设——
 * 攻击者换个值就换了个身份。
 * <p>
 * 因此默认只用 {@code request.getRemoteAddr()}；只有确实部署在可信反向代理之后，
 * 且反代用 <code>proxy_set_header X-Forwarded-For $remote_addr</code>
 * **覆写**（而不是 <code>$proxy_add_x_forwarded_for</code> 追加）该头时，
 * 才把 {@code library.security.trust-proxy} 打开。
 * <p>
 * 全项目统一走这里，避免某个入口漏改导致口径不一致。
 */
@Component
public class ClientIpResolver {

    /** 是否信任反向代理传来的 X-Forwarded-For（默认关闭） */
    @Value("${library.security.trust-proxy:false}")
    private boolean trustProxy;

    /** IPv6 文本表示最长 45 字符（含内嵌 IPv4 与 zone id 的情况） */
    private static final int MAX_IP_LENGTH = 45;

    /**
     * 解析客户端 IP。取不到或格式不合法时返回 null，调用方自行决定是否落库。
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (trustProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff) && !"unknown".equalsIgnoreCase(xff.trim())) {
                // 多级代理时形如 "client, proxy1, proxy2"，第一段才是客户端
                int idx = xff.indexOf(',');
                String first = idx > 0 ? xff.substring(0, idx) : xff;
                String clean = sanitize(first);
                if (clean != null) {
                    return clean;
                }
                // 头里有脏数据（超长、带脚本、乱码）就整段丢弃，回落到真实连接地址。
                // 不校验的话，一个超长 XFF 会把 client_ip 列（varchar(64)）撑爆，
                // 签署接口直接 500——本该是"记个 IP"，结果让用户签不了字
            }
        }
        String remote = sanitize(request.getRemoteAddr());
        return remote != null ? remote : request.getRemoteAddr();
    }

    /**
     * 只放行合法 IP 字面量：数字、点（IPv4）、冒号（IPv6）、十六进制字母、
     * 以及 IPv6 zone id 的百分号；长度不超过 45。
     *
     * @return 清洗后的 IP，不合法返回 null
     */
    private String sanitize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || s.length() > MAX_IP_LENGTH) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || c == '.' || c == ':'
                    || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == '%';
            if (!ok) {
                return null;
            }
        }
        return s;
    }
}
