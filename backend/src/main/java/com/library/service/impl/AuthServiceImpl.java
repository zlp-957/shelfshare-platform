package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.entity.User;
import com.library.mapper.UserMapper;
import com.library.service.AuthService;
import com.library.util.JwtUtil;
import com.library.vo.LoginVO;
import com.library.vo.SelfRegisterVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 登录失败计数：账号 -> 失败信息（单机内存实现，重启后清零） */
    private final Map<String, LoginAttempt> attempts = new ConcurrentHashMap<>();

    /**
     * 失败计数表的容量上限。
     * <p>
     * 这张表按「账号」分桶，而账号名是攻击者可以随便编的，所以必须有硬上限，
     * 否则一个脚本就能把它撑成内存炸弹。正常情况下活跃失败账号远低于这个量级。
     */
    private static final int MAX_ATTEMPT_ENTRIES = 10000;

    @Override
    public Map<String, Object> login(LoginVO vo) {
        String account = vo.getAccount() == null ? "" : vo.getAccount().trim();
        checkLoginLock(account);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getAccount, account));
        if (user == null) {
            recordLoginFail(account);
            throw new BusinessException("账号或密码错误");
        }
        if (!encoder.matches(vo.getPassword(), user.getPassword())) {
            recordLoginFail(account);
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == Constants.USER_STATUS_DISABLED) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        // 登录成功，清除失败计数
        attempts.remove(account);

        String token = jwtUtil.generateToken(user.getId(), user.getAccount(), user.getRole(),
                user.getTokenVersion());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("account", user.getAccount());
        result.put("name", user.getName());
        result.put("role", user.getRole());
        return result;
    }

    @Override
    public String register(SelfRegisterVO vo) {
        // 账号不由用户填写，所以这里没有账号格式校验；姓名/密码/手机号由注解校验
        if (!StringUtils.hasText(vo.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        checkPasswordStrength(vo.getPassword());

        // 并发注册时两个人可能算出同一个号，靠 account 唯一键兜底：撞了就重算
        for (int attempt = 1; attempt <= 5; attempt++) {
            String account = nextReaderAccount();
            User user = new User();
            user.setAccount(account);
            user.setPassword(encoder.encode(vo.getPassword()));
            user.setName(vo.getName().trim());
            user.setPhone(vo.getPhone().trim());
            user.setRole(Constants.ROLE_READER);
            user.setStatus(Constants.USER_STATUS_NORMAL);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            try {
                userMapper.insert(user);
                return account;
            } catch (DuplicateKeyException e) {
                // 被别的注册请求抢先占了这个号，重算下一个
            }
        }
        throw new BusinessException("注册请求过于集中，请稍后重试");
    }

    /**
     * 生成下一个读者账号：R + 当年年份 + 4 位序号，如 R20260005。
     * <p>
     * 采用「找第一个空位」而不是「最大号 +1」：
     * 历史数据里可能有 9999 这类手工号，取最大值再加一会直接越界成 5 位，格式就不对了。
     * 按序补空位既不会越界，也能自然绕过这些异常号。
     */
    private String nextReaderAccount() {
        String prefix = "R" + LocalDateTime.now().getYear();
        List<User> existing = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getAccount)
                .likeRight(User::getAccount, prefix));
        Set<String> used = new HashSet<>();
        for (User u : existing) {
            used.add(u.getAccount());
        }
        for (int seq = 1; seq <= 9999; seq++) {
            String candidate = prefix + String.format("%04d", seq);
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("本年度读者账号已分配完毕，请联系管理员处理");
    }

    /**
     * 统一密码强度校验，与"修改密码""新增读者"保持一致
     */
    private void checkPasswordStrength(String password) {
        if (!StringUtils.hasText(password) || password.length() < Constants.PASSWORD_MIN_LENGTH) {
            throw new BusinessException("密码长度不能少于" + Constants.PASSWORD_MIN_LENGTH + "位");
        }
        if (password.length() > Constants.PASSWORD_MAX_LENGTH) {
            throw new BusinessException("密码长度不能超过" + Constants.PASSWORD_MAX_LENGTH + "位");
        }
    }

    /**
     * 登录失败限流：连续失败 5 次锁定 15 分钟，防止暴力破解
     */
    private void checkLoginLock(String account) {
        LoginAttempt attempt = attempts.get(account);
        if (attempt == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (attempt.lockUntil != null && attempt.lockUntil.isAfter(now)) {
            long minutes = java.time.Duration.between(now, attempt.lockUntil).toMinutes() + 1;
            throw new BusinessException("登录失败次数过多，请 " + minutes + " 分钟后再试");
        }
        // 锁定已过期，重置计数
        if (attempt.lockUntil != null && !attempt.lockUntil.isAfter(now)) {
            attempts.remove(account);
        }
    }

    private void recordLoginFail(String account) {
        if (!StringUtils.hasText(account)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // 容量保护：条目超过阈值时先清理长时间未活动的记录。
        // 旧实现只清理「已锁定且已解锁」的条目，而失败 1~4 次就停手的记录
        // lockUntil 恒为 null，永远删不掉——攻击者拿 5 万个随机账号各失败一次，
        // 这张表只增不减，是实打实的内存泄漏。
        if (attempts.size() >= MAX_ATTEMPT_ENTRIES) {
            attempts.entrySet().removeIf(entry -> entry.getValue().isStale(now));
            // 清理后仍然超限，说明是短时间内的海量随机账号：宁可不记，也不再涨。
            // 代价是这批假账号不会被锁，但它们本来就不是真实用户，爆破不到任何东西。
            if (attempts.size() >= MAX_ATTEMPT_ENTRIES) {
                return;
            }
        }
        LoginAttempt attempt = attempts.computeIfAbsent(account, k -> new LoginAttempt());
        attempt.failCount++;
        attempt.lastFailTime = now;
        if (attempt.failCount >= Constants.LOGIN_MAX_FAIL_COUNT) {
            attempt.lockUntil = now.plusMinutes(Constants.LOGIN_LOCK_MINUTES);
            attempt.failCount = 0;
        }
    }

    private static class LoginAttempt {
        private int failCount;
        private LocalDateTime lockUntil;
        /** 最后一次失败时间，用于判定条目是否可以回收 */
        private LocalDateTime lastFailTime;

        /**
         * 是否已过保鲜期：最后一次失败距今超过一个锁定窗口，条目可以回收
         */
        private boolean isStale(LocalDateTime now) {
            LocalDateTime base = lastFailTime;
            if (base == null) {
                return true;
            }
            return base.plusMinutes(Constants.LOGIN_LOCK_MINUTES).isBefore(now);
        }
    }
}
