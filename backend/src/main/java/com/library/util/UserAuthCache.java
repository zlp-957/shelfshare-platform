package com.library.util;

import com.library.entity.User;
import com.library.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录态用户缓存。
 * <p>
 * 拦截器每个请求都要读一次 user 表（校验状态 + token_version），
 * 一个页面通常并发 5~10 个接口，等于每次操作多打十几次库。
 * <p>
 * 缓存 30 秒，并暴露 {@link #evict(Long)} 供「禁用账号 / 改密 / 重置密码」主动失效。
 * 为什么不用长 TTL：token_version 校验是改密后旧 token 立即作废的唯一手段，
 * 缓存太久等于把这个安全特性打了个折扣。30 秒是「扛住页面并发」和
 * 「撤销及时性」之间的折中；多实例部署时其他节点最多延迟一个 TTL。
 */
@Component
public class UserAuthCache {

    /** 缓存有效期：30 秒 */
    private static final long TTL_MILLIS = 30_000L;

    /** 容量上限，防止异常场景无限增长 */
    private static final int MAX_ENTRIES = 5000;

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    @Autowired
    private UserMapper userMapper;

    /**
     * 取用户（优先命中缓存）
     */
    public User get(Long userId) {
        if (userId == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        Entry entry = cache.get(userId);
        if (entry != null && entry.expireAt > now) {
            return entry.user;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            // 账号已删除：缓存一个空值没必要，清掉即可
            cache.remove(userId);
            return null;
        }
        if (cache.size() >= MAX_ENTRIES) {
            cache.entrySet().removeIf(e -> e.getValue().expireAt <= now);
            if (cache.size() >= MAX_ENTRIES) {
                // 清完过期项仍然满，说明都是活跃会话。
                // 此时不能再 put——否则「上限」形同虚设，表会一直涨。
                // 代价是这次少一次缓存命中，直接返回查库结果即可
                return user;
            }
        }
        cache.put(userId, new Entry(user, now + TTL_MILLIS));
        return user;
    }

    /**
     * 主动失效：禁用、改密、重置密码后必须调用，否则旧 token 还能多用 30 秒
     */
    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    private static class Entry {
        private final User user;
        private final long expireAt;

        private Entry(User user, long expireAt) {
            this.user = user;
            this.expireAt = expireAt;
        }
    }
}
