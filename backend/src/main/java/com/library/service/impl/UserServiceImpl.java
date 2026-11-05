package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.common.PageUtil;
import com.library.entity.User;
import com.library.mapper.UserMapper;
import com.library.service.UserService;
import com.library.util.UserAuthCache;
import com.library.util.UserContext;
import com.library.vo.RegisterVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserAuthCache userAuthCache;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public Page<User> pageReaders(Integer page, Integer size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, Constants.ROLE_READER);
        if (StringUtils.hasText(keyword)) {
            // 支持按手机号搜：管理员联系读者时手里通常只有手机号
            wrapper.and(w -> w.like(User::getAccount, keyword)
                    .or().like(User::getName, keyword)
                    .or().like(User::getPhone, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> result = userMapper.selectPage(PageUtil.of(page, size), wrapper);
        // 隐藏密码
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }

    @Override
    public void addReader(RegisterVO vo) {
        // 后端二次校验
        if (!StringUtils.hasText(vo.getAccount())
                || !vo.getAccount().matches(Constants.REGEX_READER_ACCOUNT)) {
            throw new BusinessException("读者账号格式不正确，应为 R + 8位数字，如 R20260001");
        }
        checkPasswordStrength(vo.getPassword());
        if (!StringUtils.hasText(vo.getName())) {
            throw new BusinessException("请填写真实姓名");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getAccount, vo.getAccount()));
        if (exists != null && exists > 0) {
            throw new BusinessException("该账号已存在");
        }
        User user = new User();
        user.setAccount(vo.getAccount());
        user.setPassword(encoder.encode(vo.getPassword()));
        user.setName(vo.getName().trim());
        // 手机号可选：填了就校验，没填就是 null
        if (StringUtils.hasText(vo.getPhone())) {
            String p = vo.getPhone().trim();
            checkPhone(p);
            user.setPhone(p);
        }
        user.setRole(Constants.ROLE_READER);
        user.setStatus(Constants.USER_STATUS_NORMAL);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    @Override
    public void updateReader(Long id, String name, String password, String phone) {
        User user = userMapper.selectById(id);
        if (user == null || user.getRole() != Constants.ROLE_READER) {
            throw new BusinessException("读者不存在");
        }
        if (StringUtils.hasText(name)) {
            user.setName(name.trim());
        }
        if (StringUtils.hasText(password)) {
            checkPasswordStrength(password);
            user.setPassword(encoder.encode(password));
            // 管理员重置了读者密码，该读者旧 token 必须立即失效
            user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
            userAuthCache.evict(id);
        }
        // phone 为 null 表示不修改；空串表示清空；有值则校验格式
        if (phone != null) {
            String p = phone.trim();
            if (p.isEmpty()) {
                user.setPhone(null);
            } else {
                checkPhone(p);
                user.setPhone(p);
            }
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 手机号格式校验：只认 11 位大陆手机号，不做验证码、不要求唯一（采集用途）
     */
    private void checkPhone(String phone) {
        if (!phone.matches("^1\\d{10}$")) {
            throw new BusinessException("手机号格式不正确，应为 11 位数字");
        }
    }

    @Override
    public void toggleStatus(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("读者不存在");
        }
        // 只允许对读者账号操作，避免把管理员账号禁用导致系统被锁死
        if (user.getRole() != Constants.ROLE_READER) {
            throw new BusinessException("只能对读者账号执行启用/禁用操作");
        }
        // 禁止禁用当前登录的自己
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(user.getId())) {
            throw new BusinessException("不能禁用当前登录的账号");
        }
        user.setStatus(user.getStatus() == Constants.USER_STATUS_NORMAL
                ? Constants.USER_STATUS_DISABLED : Constants.USER_STATUS_NORMAL);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        // 立即失效缓存：否则被禁用的账号还能凭旧 token 多用 30 秒
        userAuthCache.evict(id);
    }

    @Override
    public User getCurrentUser() {
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    @Override
    public void updateProfile(String name) {
        // 空白姓名直接报错，避免"提示成功但实际未修改"
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("请填写真实姓名");
        }
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setName(name.trim());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void updatePassword(String oldPassword, String newPassword) {
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!encoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        checkPasswordStrength(newPassword);
        user.setPassword(encoder.encode(newPassword));
        // 令牌版本自增：让该用户此前所有 token 立即失效。
        // 否则密码泄露后改了密码，攻击者手里的旧 token 仍能用满 24 小时
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        userAuthCache.evict(user.getId());
    }

    @Override
    public void updateAvatar(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            throw new BusinessException("头像地址不能为空");
        }
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setAvatar(avatarUrl);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void updatePhone(String phone) {
        String p = phone == null ? "" : phone.trim();
        // 允许清空手机号
        if (p.isEmpty()) {
            User user = userMapper.selectById(UserContext.getUserId());
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            user.setPhone(null);
            user.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(user);
            return;
        }
        // 只做 11 位手机号格式校验，不做验证码、不要求唯一（采集用途）
        checkPhone(p);
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPhone(p);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 统一密码强度校验：注册、管理员新增读者、修改密码使用同一套规则
     */
    private void checkPasswordStrength(String password) {
        if (!StringUtils.hasText(password) || password.length() < Constants.PASSWORD_MIN_LENGTH) {
            throw new BusinessException("密码长度不能少于" + Constants.PASSWORD_MIN_LENGTH + "位");
        }
        if (password.length() > Constants.PASSWORD_MAX_LENGTH) {
            throw new BusinessException("密码长度不能超过" + Constants.PASSWORD_MAX_LENGTH + "位");
        }
    }
}
