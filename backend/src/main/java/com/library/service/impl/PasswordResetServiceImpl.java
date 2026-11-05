package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.common.PageUtil;
import com.library.entity.PasswordResetRequest;
import com.library.entity.User;
import com.library.mapper.PasswordResetRequestMapper;
import com.library.mapper.UserMapper;
import com.library.service.PasswordResetService;
import com.library.util.UserAuthCache;
import com.library.util.UserContext;
import com.library.vo.PasswordResetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 密码重置服务实现
 * <p>
 * 安全要点：
 * 1. 只按「数据库里该账号绑定的姓名+手机号」匹配，用户申请时不能自选联系方式，
 *    否则攻击者填自己的手机号就能劫持别人的账号。
 * 2. 重置码一次性、30 分钟过期，用过即失效。
 * 3. 账号不存在与重置码错误返回同一句提示，避免被用来枚举账号。
 */
@Slf4j
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 重置码失败计数：用户id -> 连续输错次数。
     * <p>
     * 自助重置换码是公开接口（用户正是登不进去才用），如果不限次数，
     * 6 位码在 30 分钟窗口内是有可能被在线枚举的。连续错 5 次就作废本次申请。
     */
    private static final Map<Long, Integer> CODE_FAIL = new ConcurrentHashMap<>();

    @Autowired
    private PasswordResetRequestMapper resetMapper;
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserAuthCache userAuthCache;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void apply(String name, String phone) {
        String n = name == null ? "" : name.trim();
        String p = phone == null ? "" : phone.trim();
        if (!StringUtils.hasText(n)) {
            throw new BusinessException("请填写姓名");
        }
        if (!p.matches("^1\\d{10}$")) {
            throw new BusinessException("手机号格式不正确，应为 11 位数字");
        }

        // 必须是库里已绑定的「姓名 + 手机号」同时匹配，不能让申请人自行指定联系方式
        List<User> matched = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getName, n)
                .eq(User::getPhone, p)
                .eq(User::getRole, Constants.ROLE_READER)
                .eq(User::getStatus, Constants.USER_STATUS_NORMAL)
                .orderByDesc(User::getId));
        if (matched.isEmpty()) {
            throw new BusinessException("未找到匹配的读者，请核对姓名与手机号；若从未填写手机号，请携带身份证到馆处理");
        }
        // 同名同手机号命中多个账号时不能"取最新那个"——那样旧账号永远找不回密码，
        // 还容易把重置权限发到错误的账号上，交给人工最稳妥
        if (matched.size() > 1) {
            throw new BusinessException("检测到多个同名同手机号的账号，无法自动匹配，请携带身份证到馆由工作人员处理");
        }
        User user = matched.get(0);

        // 冷却：同一账号 1 分钟内只能提交一次。
        // 必须放在最前面——本方法整体是 @Transactional，如果先改了状态再抛异常，
        // 状态改动会被一起回滚，看起来"提示了却没生效"
        LocalDateTime cool = LocalDateTime.now().minusMinutes(Constants.RESET_APPLY_COOLDOWN_MINUTES);
        Long recent = resetMapper.selectCount(new LambdaQueryWrapper<PasswordResetRequest>()
                .eq(PasswordResetRequest::getUserId, user.getId())
                .gt(PasswordResetRequest::getCreateTime, cool));
        if (recent != null && recent > 0) {
            throw new BusinessException("你刚刚提交过申请，请稍后再试，或等待工作人员联系");
        }

        // 已有待处理/已发码的申请时直接返回，避免重复建单刷屏。
        // 但「已发码」分两种：码还没过期（真活跃）和码已过期（这次申请已经废了）。
        // 后者必须就地作废并放行新申请，否则会形成死锁：
        //   读者再申请 → 被挡住不建单；管理员发码 → 状态不是待处理，拒绝；
        //   管理员拒绝 → 状态不是待处理，也拒绝。三条路全堵死，只能改库
        List<PasswordResetRequest> actives = resetMapper.selectList(
                new LambdaQueryWrapper<PasswordResetRequest>()
                        .eq(PasswordResetRequest::getUserId, user.getId())
                        .in(PasswordResetRequest::getStatus,
                                Constants.RESET_STATUS_PENDING, Constants.RESET_STATUS_CODE_SENT)
                        .orderByDesc(PasswordResetRequest::getId));
        boolean hasLive = false;
        for (PasswordResetRequest a : actives) {
            boolean codeExpired = a.getStatus() == Constants.RESET_STATUS_CODE_SENT
                    && (a.getCodeExpire() == null
                    || a.getCodeExpire().isBefore(LocalDateTime.now()));
            if (codeExpired) {
                resetMapper.update(null, new LambdaUpdateWrapper<PasswordResetRequest>()
                        .eq(PasswordResetRequest::getId, a.getId())
                        .eq(PasswordResetRequest::getStatus, Constants.RESET_STATUS_CODE_SENT)
                        .set(PasswordResetRequest::getStatus, Constants.RESET_STATUS_REJECTED)
                        .set(PasswordResetRequest::getResetCode, null)
                        .set(PasswordResetRequest::getRemark, "重置码已过期，系统自动作废")
                        .set(PasswordResetRequest::getUpdateTime, LocalDateTime.now()));
                log.info("过期重置申请已自动作废 requestId={} userId={}", a.getId(), user.getId());
                continue;
            }
            hasLive = true;
        }
        if (hasLive) {
            return;
        }

        PasswordResetRequest req = new PasswordResetRequest();
        req.setUserId(user.getId());
        req.setAccount(user.getAccount());
        req.setApplyName(n);
        req.setApplyPhone(p);
        req.setStatus(Constants.RESET_STATUS_PENDING);
        req.setCreateTime(LocalDateTime.now());
        req.setUpdateTime(LocalDateTime.now());
        resetMapper.insert(req);
        log.info("密码找回申请已提交 userId={} account={}", user.getId(), user.getAccount());
    }

    @Override
    public Page<PasswordResetVO> page(Integer page, Integer size, Integer status) {
        int pageNo = PageUtil.normalizePage(page);
        int pageSize = PageUtil.normalizeSize(size);

        LambdaQueryWrapper<PasswordResetRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(PasswordResetRequest::getStatus, status);
        }
        // 待处理的排前面，其余按申请时间倒序
        wrapper.orderByAsc(PasswordResetRequest::getStatus)
                .orderByDesc(PasswordResetRequest::getId);
        Page<PasswordResetRequest> p = resetMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        Page<PasswordResetVO> voPage = new Page<>(pageNo, pageSize, p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional
    public String generateCode(Long id) {
        PasswordResetRequest req = resetMapper.selectById(id);
        if (req == null) {
            throw new BusinessException("申请不存在");
        }
        // 「已发码」也允许重发：用户可能把码弄丢了，或者码过期但申请还没被清理。
        // 卡着不让重发的话，管理员只能眼看着这条单坏掉，最后又回到改库。
        // 重发会覆盖旧码（旧码立即失效），失败计数一并清零。
        if (req.getStatus() != Constants.RESET_STATUS_PENDING
                && req.getStatus() != Constants.RESET_STATUS_CODE_SENT) {
            throw new BusinessException("该申请已完成或被拒绝，无法发放重置码，请让读者重新提交申请");
        }
        String code = String.format("%06d", RANDOM.nextInt(1000000));
        LocalDateTime expire = LocalDateTime.now()
                .plusMinutes(Constants.RESET_CODE_EXPIRE_MINUTES);

        // 条件更新：只有仍是「待处理」或「已发码」才推进，防止两人同时点
        int updated = resetMapper.update(null, new LambdaUpdateWrapper<PasswordResetRequest>()
                .eq(PasswordResetRequest::getId, id)
                .in(PasswordResetRequest::getStatus,
                        Constants.RESET_STATUS_PENDING, Constants.RESET_STATUS_CODE_SENT)
                .set(PasswordResetRequest::getStatus, Constants.RESET_STATUS_CODE_SENT)
                .set(PasswordResetRequest::getResetCode, code)
                .set(PasswordResetRequest::getCodeExpire, expire)
                .set(PasswordResetRequest::getRemark, null)
                .set(PasswordResetRequest::getHandledBy, UserContext.getUserId())
                .set(PasswordResetRequest::getHandleTime, LocalDateTime.now())
                .set(PasswordResetRequest::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("该申请已被其他人处理，请刷新列表");
        }
        // 重发等于给用户一次全新的机会，之前的输错次数不该继续压着他
        CODE_FAIL.remove(req.getUserId());
        log.info("已发放重置码 requestId={} account={} 有效期至 {}",
                id, req.getAccount(), expire);
        return code;
    }

    @Override
    @Transactional
    public void reject(Long id, String remark) {
        PasswordResetRequest req = resetMapper.selectById(id);
        if (req == null) {
            throw new BusinessException("申请不存在");
        }
        // 同样放开「已发码」：码发错了、用户联系不上了，管理员得能作废它
        int updated = resetMapper.update(null, new LambdaUpdateWrapper<PasswordResetRequest>()
                .eq(PasswordResetRequest::getId, id)
                .in(PasswordResetRequest::getStatus,
                        Constants.RESET_STATUS_PENDING, Constants.RESET_STATUS_CODE_SENT)
                .set(PasswordResetRequest::getStatus, Constants.RESET_STATUS_REJECTED)
                .set(PasswordResetRequest::getRemark, remark)
                .set(PasswordResetRequest::getResetCode, null)
                .set(PasswordResetRequest::getHandledBy, UserContext.getUserId())
                .set(PasswordResetRequest::getHandleTime, LocalDateTime.now())
                .set(PasswordResetRequest::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("该申请已处理，无需重复操作");
        }
        CODE_FAIL.remove(req.getUserId());
    }

    @Override
    @Transactional
    public int expireStale() {
        LocalDateTime now = LocalDateTime.now();
        return resetMapper.update(null, new LambdaUpdateWrapper<PasswordResetRequest>()
                .eq(PasswordResetRequest::getStatus, Constants.RESET_STATUS_CODE_SENT)
                .lt(PasswordResetRequest::getCodeExpire, now)
                .set(PasswordResetRequest::getStatus, Constants.RESET_STATUS_REJECTED)
                .set(PasswordResetRequest::getResetCode, null)
                .set(PasswordResetRequest::getRemark, "重置码已过期，系统自动作废")
                .set(PasswordResetRequest::getUpdateTime, now));
    }

    @Override
    @Transactional
    public void resetPassword(String account, String code, String newPassword) {
        String acc = account == null ? "" : account.trim();
        String c = code == null ? "" : code.trim();
        if (!StringUtils.hasText(acc) || !StringUtils.hasText(c)) {
            throw new BusinessException("请填写账号和重置码");
        }
        if (!StringUtils.hasText(newPassword)
                || newPassword.length() < Constants.PASSWORD_MIN_LENGTH
                || newPassword.length() > Constants.PASSWORD_MAX_LENGTH) {
            throw new BusinessException("密码长度需在 " + Constants.PASSWORD_MIN_LENGTH
                    + "-" + Constants.PASSWORD_MAX_LENGTH + " 位之间");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getAccount, acc));
        // 账号不存在与码错误给同一句提示，避免被拿来枚举账号
        if (user == null) {
            throw new BusinessException("账号或重置码错误");
        }

        List<PasswordResetRequest> found = resetMapper.selectList(
                new LambdaQueryWrapper<PasswordResetRequest>()
                        .eq(PasswordResetRequest::getUserId, user.getId())
                        .eq(PasswordResetRequest::getStatus, Constants.RESET_STATUS_CODE_SENT)
                        .eq(PasswordResetRequest::getResetCode, c)
                        .orderByDesc(PasswordResetRequest::getId)
                        .last("LIMIT 1"));
        PasswordResetRequest req = found.isEmpty() ? null : found.get(0);
        if (req == null) {
            // 码不对：计数，连错达到上限就把本次申请作废，防止在线枚举
            int fails = CODE_FAIL.merge(user.getId(), 1, Integer::sum);
            if (fails >= Constants.RESET_CODE_MAX_FAIL) {
                // 关键：作废必须开一个真正独立的事务（REQUIRES_NEW）。
                // 本方法整体是 @Transactional 且下面马上要抛异常，
                // 如果作废只是加入当前事务（默认 REQUIRED），会被一起回滚，等于没做
                TransactionTemplate tt = new TransactionTemplate(transactionManager);
                tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tt.executeWithoutResult(s ->
                        resetMapper.update(null, new LambdaUpdateWrapper<PasswordResetRequest>()
                                .eq(PasswordResetRequest::getUserId, user.getId())
                                .eq(PasswordResetRequest::getStatus, Constants.RESET_STATUS_CODE_SENT)
                                .set(PasswordResetRequest::getStatus, Constants.RESET_STATUS_REJECTED)
                                .set(PasswordResetRequest::getRemark, "重置码错误次数过多，已作废")
                                .set(PasswordResetRequest::getUpdateTime, LocalDateTime.now())));
                CODE_FAIL.remove(user.getId());
                log.warn("重置码连续输错 {} 次，已作废申请 userId={}",
                        Constants.RESET_CODE_MAX_FAIL, user.getId());
                throw new BusinessException("重置码错误次数过多，本次申请已作废，请重新提交找回申请");
            }
            throw new BusinessException("账号或重置码错误");
        }
        if (req.getCodeExpire() == null || req.getCodeExpire().isBefore(LocalDateTime.now())) {
            // 过期单直接置为失效，避免用户反复尝试同一个码
            resetMapper.update(null, new LambdaUpdateWrapper<PasswordResetRequest>()
                    .eq(PasswordResetRequest::getId, req.getId())
                    .eq(PasswordResetRequest::getStatus, Constants.RESET_STATUS_CODE_SENT)
                    .set(PasswordResetRequest::getStatus, Constants.RESET_STATUS_REJECTED)
                    .set(PasswordResetRequest::getRemark, "重置码已过期")
                    .set(PasswordResetRequest::getUpdateTime, LocalDateTime.now()));
            throw new BusinessException("重置码已过期，请重新提交找回申请");
        }

        // 改密码 + 令牌版本自增：该用户此前登录的会话全部失效
        user.setPassword(encoder.encode(newPassword));
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        // 缓存必须同步失效：否则拦截器还可能拿着旧快照，
        // 用新密码登录反而被判成「密码已变更」，30 秒内反复踢人
        userAuthCache.evict(user.getId());
        CODE_FAIL.remove(user.getId());

        // 申请单置完成并清掉重置码（updateById 会忽略 null，这里必须显式 set）
        resetMapper.update(null, new LambdaUpdateWrapper<PasswordResetRequest>()
                .eq(PasswordResetRequest::getId, req.getId())
                .set(PasswordResetRequest::getStatus, Constants.RESET_STATUS_DONE)
                .set(PasswordResetRequest::getResetCode, null)
                .set(PasswordResetRequest::getHandleTime, LocalDateTime.now())
                .set(PasswordResetRequest::getUpdateTime, LocalDateTime.now()));
        log.info("读者自助重置密码成功 userId={} account={}", user.getId(), user.getAccount());
    }

    @Override
    public Long pendingCount() {
        Long n = resetMapper.selectCount(new LambdaQueryWrapper<PasswordResetRequest>()
                .eq(PasswordResetRequest::getStatus, Constants.RESET_STATUS_PENDING));
        return n == null ? 0L : n;
    }

    private PasswordResetVO toVO(PasswordResetRequest r) {
        PasswordResetVO vo = new PasswordResetVO();
        vo.setId(r.getId());
        vo.setUserId(r.getUserId());
        vo.setAccount(r.getAccount());
        vo.setApplyName(r.getApplyName());
        vo.setApplyPhone(r.getApplyPhone());
        vo.setStatus(r.getStatus());
        vo.setStatusText(statusText(r.getStatus()));
        vo.setCodeExpire(r.getCodeExpire());
        vo.setRemark(r.getRemark());
        vo.setCreateTime(r.getCreateTime());
        vo.setHandleTime(r.getHandleTime());
        // 列表不回显重置码，只有生成那一刻单独返回
        return vo;
    }

    private String statusText(Integer s) {
        if (s == null) {
            return "未知";
        }
        switch (s) {
            case Constants.RESET_STATUS_PENDING:
                return "待核实";
            case Constants.RESET_STATUS_CODE_SENT:
                return "已发重置码";
            case Constants.RESET_STATUS_DONE:
                return "已完成";
            case Constants.RESET_STATUS_REJECTED:
                return "已拒绝/过期";
            default:
                return "未知";
        }
    }
}
