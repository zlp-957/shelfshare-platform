package com.library.config;

import com.library.common.Constants;
import com.library.entity.User;
import com.library.util.JwtUtil;
import com.library.util.UserAuthCache;
import com.library.util.UserContext;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器：校验 JWT，解析用户信息，并校验账号当前状态
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户状态走带 TTL 的缓存：每个请求都查一次库会成为热点。
     * 关键操作（禁用/改密）会主动 evict，撤销依然是即时的。
     */
    @Autowired
    private UserAuthCache userAuthCache;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            writeError(response, 401, "未登录或登录已过期");
            return false;
        }
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            writeError(response, 401, "登录已过期，请重新登录");
            return false;
        }
        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            writeError(response, 401, "登录凭证无效，请重新登录");
            return false;
        }
        // 校验账号实时状态：被禁用/删除的账号，旧 token 立即失效
        User user = userAuthCache.get(userId);
        if (user == null) {
            writeError(response, 401, "账号不存在，请重新登录");
            return false;
        }
        if (user.getStatus() != null && user.getStatus() == Constants.USER_STATUS_DISABLED) {
            writeError(response, 401, "账号已被禁用，请联系管理员");
            return false;
        }
        // 令牌版本校验：修改密码后 token_version 自增，此前签发的 token 全部失效。
        // JWT 本身无状态、签发后无法主动作废，版本号是最轻量的吊销方案，无需引入 Redis
        Number tokenVersion = claims.get("tv", Number.class);
        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenVersion == null || tokenVersion.intValue() != currentVersion) {
            writeError(response, 401, "密码已变更，请重新登录");
            return false;
        }
        // 角色以数据库为准，避免 token 中角色过期导致越权
        UserContext.set(userId, user.getAccount(), user.getRole());
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String msg) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + msg + "\"}");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
