package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.entity.Borrow;
import com.library.entity.PayOrder;
import com.library.entity.PurchaseOrder;
import com.library.entity.User;
import com.library.mapper.BorrowMapper;
import com.library.mapper.PayOrderMapper;
import com.library.mapper.PurchaseOrderMapper;
import com.library.mapper.UserMapper;
import com.library.service.BorrowService;
import com.library.service.PickupService;
import com.library.service.PurchaseService;
import com.library.util.UserContext;
import com.library.vo.BorrowVO;
import com.library.vo.PickupPendingVO;
import com.library.vo.PickupResultVO;
import com.library.vo.PurchaseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 取书服务实现
 * <p>
 * 安全设计：
 * 1. 码 30 分钟有效，过期即作废，泄露窗口有限；
 * 2. 核销成功后立即作废码，朋友偷看到的码不能用第二次；
 * 3. 按「管理员账号 + 终端」限流，阻断暴力穷举；
 * 4. 真正防冒领的是「商户核对姓名」，码只负责快速定位。
 */
@Slf4j
@Service
public class PickupServiceImpl implements PickupService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 查询失败窗口：限流键 -> 计数与窗口起点。
     * <p>
     * 之所以按终端而不是按"输错的码"计数：枚举攻击是每次换一个码去试，
     * 按码计数永远累加不起来。只有按终端才有意义。
     * <p>
     * 键里再拼上管理员 id：终端 IP 可以被伪造头或换网络绕过，
     * 但当前登录的管理员身份绕不过去，两个维度叠加才真正提高成本。
     * 同时也避免一个管理员手误把整台机器的其他管理员一起锁死。
     */
    private static final Map<String, FailWindow> QUERY_FAIL = new ConcurrentHashMap<>();

    private static class FailWindow {
        int count;
        long start;

        FailWindow(long start) {
            this.count = 1;
            this.start = start;
        }
    }

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private BorrowMapper borrowMapper;
    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;
    @Autowired
    private PayOrderMapper payOrderMapper;
    @Autowired
    private BorrowService borrowService;
    @Autowired
    private PurchaseService purchaseService;

    @Override
    @Transactional
    public String generateCode() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 没有待取需求就不该有码：码的唯一用途是让馆员快速定位"这个人有什么要给"。
        // 放开生成的话，码成了一种可以随时出示的身份凭证，
        // 泄露面和被冒认的风险都凭空多出来
        if (countPending(userId) == 0) {
            throw new BusinessException("您当前没有可取的图书，借阅需先支付押金、购买需完成支付，才能生成取书码");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = now.plusMinutes(Constants.PICKUP_CODE_EXPIRE_MINUTES);
        // 每次生成都覆盖旧码：旧码立即失效，防止"截图给朋友"这类泄露
        for (int i = 0; i < 20; i++) {
            String code = String.format("%06d", RANDOM.nextInt(1000000));
            Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getPickupCode, code));
            if (exists != null && exists > 0) {
                continue;
            }
            try {
                userMapper.update(null, new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getPickupCode, code)
                        .set(User::getPickupCodeExpire, expire)
                        .set(User::getUpdateTime, now));
                log.info("生成取书码 userId={} code={} 有效期至 {}", userId, code, expire);
                return code;
            } catch (DuplicateKeyException e) {
                // 并发下撞了唯一键，换一个再试
            }
        }
        throw new BusinessException("生成取书码失败，请稍后重试");
    }

    @Override
    public PickupPendingVO myCodeInfo() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        PickupPendingVO vo = new PickupPendingVO();
        vo.setUserId(user.getId());
        vo.setUserAccount(user.getAccount());
        vo.setUserName(user.getName());
        int pending = countPending(userId);
        vo.setTotalCount(pending);
        if (pending == 0) {
            // 没有待取需求时码没有存在意义。惰性清理一下残留：
            // 覆盖"下单→取消/超时关单，但 30 分钟码还活着"的窗口期
            if (StringUtils.hasText(user.getPickupCode())) {
                invalidateCode(userId);
            }
            return vo;
        }
        // 过期的码不再展示，避免用户报了才发现失效
        if (StringUtils.hasText(user.getPickupCode())
                && user.getPickupCodeExpire() != null
                && user.getPickupCodeExpire().isAfter(LocalDateTime.now())) {
            vo.setPickupCode(user.getPickupCode());
            vo.setCodeExpire(user.getPickupCodeExpire());
        }
        return vo;
    }

    @Override
    public PickupPendingVO pending(String codeOrAccount, String clientIp) {
        User user = resolveUser(codeOrAccount, clientIp);
        return buildPending(user, codeOrAccount);
    }

    @Override
    public PickupResultVO pickup(String codeOrAccount, List<Long> borrowIds,
                                 List<Long> orderIds, String clientIp) {
        User user = resolveUser(codeOrAccount, clientIp);
        boolean noBorrow = borrowIds == null || borrowIds.isEmpty();
        boolean noOrder = orderIds == null || orderIds.isEmpty();
        if (noBorrow && noOrder) {
            throw new BusinessException("请先勾选要办理的图书");
        }

        PickupResultVO result = new PickupResultVO();
        // 注意：本方法不加 @Transactional —— 每条核销走各自的独立事务，
        // 一本失败（比如书找不到）不该拖累其他几本，支持部分成功
        if (!noBorrow) {
            for (Long bid : borrowIds) {
                String bookName = null;
                try {
                    Borrow b = borrowMapper.selectById(bid);
                    if (b == null || !b.getUserId().equals(user.getId())) {
                        result.getFailures().add("借阅单 #" + bid + " 不属于该读者");
                        continue;
                    }
                    borrowService.checkOutById(bid);
                    result.setBorrowSuccess(result.getBorrowSuccess() + 1);
                } catch (Exception e) {
                    result.getFailures().add("借阅单 #" + bid + "：" + e.getMessage());
                }
            }
        }
        if (!noOrder) {
            for (Long oid : orderIds) {
                try {
                    PurchaseOrder o = purchaseOrderMapper.selectById(oid);
                    if (o == null || !o.getUserId().equals(user.getId())) {
                        result.getFailures().add("订单 #" + oid + " 不属于该读者");
                        continue;
                    }
                    purchaseService.pickupByOrderId(oid);
                    result.setPurchaseSuccess(result.getPurchaseSuccess() + 1);
                } catch (Exception e) {
                    result.getFailures().add("订单 #" + oid + "：" + e.getMessage());
                }
            }
        }

        // 只要办成了至少一件，就作废取书码——下次取书需要重新生成，
        // 这样即使码被朋友看到，也用不了第二次
        boolean anySuccess = result.getBorrowSuccess() > 0 || result.getPurchaseSuccess() > 0;
        if (anySuccess && StringUtils.hasText(user.getPickupCode())) {
            invalidateCode(user.getId());
            result.setCodeInvalidated(true);
        }
        log.info("批量取书核销 userId={} 借阅={} 购买={} 失败={}",
                user.getId(), result.getBorrowSuccess(), result.getPurchaseSuccess(),
                result.getFailures().size());
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 待取需求件数：押金已支付的待取借阅 + 已支付待取的购买订单。
     * <p>
     * 借阅单一下单就是「待取书」状态，押金付没付在状态上没区别。
     * 如果只按状态统计，读者会拿到一个"看起来能取书"的码，
     * 到馆核销时才被告知押金没付——而柜台没有代收入口，只能白跑一趟。
     * 所以这里必须再核一次支付单状态，只认真正付过钱的。
     */
    private int countPending(Long userId) {
        List<Borrow> pendings = borrowMapper.selectList(new LambdaQueryWrapper<Borrow>()
                .select(Borrow::getId, Borrow::getPayOrderNo)
                .eq(Borrow::getUserId, userId)
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_PENDING_PICKUP));
        int paidBorrows = 0;
        if (!pendings.isEmpty()) {
            List<String> orderNos = pendings.stream()
                    .map(Borrow::getPayOrderNo)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
            if (!orderNos.isEmpty()) {
                // 注意：合并支付时 N 条借阅共用一个支付单，不能数支付单条数，
                // 必须先取出「已支付的单号集合」，再按借阅逐条判定
                Set<String> paidOrderNos = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                                .select(PayOrder::getOrderNo)
                                .in(PayOrder::getOrderNo, orderNos)
                                .in(PayOrder::getStatus,
                                        Constants.PAY_STATUS_PAID,
                                        Constants.PAY_STATUS_PARTIAL_REFUNDED))
                        .stream().map(PayOrder::getOrderNo).collect(Collectors.toSet());
                paidBorrows = (int) pendings.stream()
                        .filter(b -> paidOrderNos.contains(b.getPayOrderNo()))
                        .count();
            }
        }
        Long purchases = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getUserId, userId)
                .eq(PurchaseOrder::getStatus, 1)
                .eq(PurchaseOrder::getPickupStatus, Constants.PICKUP_STATUS_PENDING));
        return paidBorrows + (purchases == null ? 0 : purchases.intValue());
    }

    private PickupPendingVO buildPending(User user, String input) {
        PickupPendingVO vo = new PickupPendingVO();
        vo.setUserId(user.getId());
        vo.setUserAccount(user.getAccount());
        vo.setUserName(user.getName());
        vo.setPhone(user.getPhone());
        if (StringUtils.hasText(input) && input.trim().equals(user.getPickupCode())) {
            vo.setPickupCode(user.getPickupCode());
            vo.setCodeExpire(user.getPickupCodeExpire());
        }
        List<BorrowVO> borrows = borrowService.listByUser(
                user.getId(), Constants.BORROW_STATUS_PENDING_PICKUP);
        List<PurchaseVO> purchases = purchaseService.listByUser(user.getId()).stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1
                        && p.getPickupStatus() != null
                        && p.getPickupStatus() == Constants.PICKUP_STATUS_PENDING)
                .collect(Collectors.toList());
        vo.setBorrows(borrows);
        vo.setPurchases(purchases);
        vo.setTotalCount(borrows.size() + purchases.size());
        return vo;
    }

    /**
     * 把输入解析成读者：R 开头按账号查，否则按取书码查（必须未过期）
     */
    private User resolveUser(String input, String clientIp) {
        if (!StringUtils.hasText(input)) {
            throw new BusinessException("请输入取书码或读者账号");
        }

        String s = input.trim();
        // 账号兜底：用户手机没电/码过期时，报账号一样能办。
        // 账号是 9 位且必须先知道，枚举成本远高于 6 位码，所以不参与限流——
        // 否则管理员手误被锁后，连正常业务都办不了
        if (s.toUpperCase().startsWith("R")) {
            User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getAccount, s.toUpperCase()));
            if (u == null) {
                throw new BusinessException("读者不存在，请核对账号");
            }
            return u;
        }

        // 按码查询才限流：这才是能被枚举的入口
        String throttleKey = throttleKey(clientIp);
        checkAllowed(throttleKey);
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPickupCode, s));
        if (u == null) {
            recordFail(throttleKey);
            throw new BusinessException("取书码无效，请核对；若已过期请让读者在页面重新生成");
        }
        if (u.getPickupCodeExpire() == null
                || !u.getPickupCodeExpire().isAfter(LocalDateTime.now())) {
            invalidateCode(u.getId());
            throw new BusinessException("取书码已过期，请让读者重新生成");
        }
        return u;
    }

    /**
     * 限流键 = 管理员 id + 终端 IP。
     * <p>
     * 只按 IP 的话，换一个伪造的 X-Forwarded-For 或者换台机器就能重新拿到额度；
     * 只按管理员的话，一个账号在多个窗口同时作业会互相挤。
     * 两者拼起来，攻击者必须同时换账号和换 IP 才能继续试。
     */
    private String throttleKey(String clientIp) {
        Long adminId = UserContext.getUserId();
        return (adminId == null ? "anon" : adminId.toString())
                + "@" + (StringUtils.hasText(clientIp) ? clientIp : "-");
    }

    /**
     * 检查该终端是否因失败过多被临时锁定
     */
    private void checkAllowed(String key) {
        FailWindow w = QUERY_FAIL.get(key);
        if (w == null) {
            return;
        }
        long lockMs = Constants.PICKUP_QUERY_LOCK_MINUTES * 60_000L;
        long elapsed = System.currentTimeMillis() - w.start;
        if (elapsed >= lockMs) {
            // 窗口已过，计数作废
            QUERY_FAIL.remove(key);
            return;
        }
        if (w.count >= Constants.PICKUP_QUERY_MAX_FAIL) {
            long remainMin = (lockMs - elapsed) / 60_000 + 1;
            throw new BusinessException("查询失败次数过多，请 " + remainMin
                    + " 分钟后再试；也可以改用读者账号查询");
        }
    }

    /**
     * 记一次查询失败。窗口过期则重新开一个窗口；
     * 顺手清理过期条目，避免 Map 无限增长
     */
    private void recordFail(String key) {
        long now = System.currentTimeMillis();
        long lockMs = Constants.PICKUP_QUERY_LOCK_MINUTES * 60_000L;
        QUERY_FAIL.compute(key, (k, v) -> {
            if (v == null || now - v.start >= lockMs) {
                return new FailWindow(now);
            }
            v.count++;
            return v;
        });
        if (QUERY_FAIL.size() > 5000) {
            QUERY_FAIL.entrySet().removeIf(e -> now - e.getValue().start >= lockMs);
        }
    }

    private void invalidateCode(Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPickupCode, null)
                .set(User::getPickupCodeExpire, null)
                .set(User::getUpdateTime, LocalDateTime.now()));
    }
}
