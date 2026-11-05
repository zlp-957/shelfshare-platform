package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.common.PageUtil;
import com.library.entity.Book;
import com.library.entity.BookItem;
import com.library.entity.BookReserve;
import com.library.entity.Borrow;
import com.library.entity.PayOrder;
import com.library.entity.User;
import com.library.mapper.BookItemMapper;
import com.library.mapper.BookMapper;
import com.library.mapper.BookReserveMapper;
import com.library.mapper.BorrowMapper;
import com.library.mapper.PayOrderMapper;
import com.library.mapper.UserMapper;
import com.library.service.BorrowService;
import com.library.service.DepositRefundService;
import com.library.service.PayService;
import com.library.service.PaymentFlowService;
import com.library.service.AgreementService;
import com.library.service.ReserveService;
import com.library.util.UserContext;
import com.library.vo.BorrowSettleVO;
import com.library.vo.BorrowVO;
import com.library.vo.PayVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 借阅服务实现（押金制 + 到馆核销）
 */
@Slf4j
@Service
public class BorrowServiceImpl implements BorrowService {

    @Autowired
    private BorrowMapper borrowMapper;
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private BookItemMapper bookItemMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private BookReserveMapper bookReserveMapper;
    @Autowired
    private PayOrderMapper payOrderMapper;
    @Autowired
    private PayService payService;
    @Autowired
    private PaymentFlowService paymentFlowService;
    @Autowired
    private AgreementService agreementService;
    @Autowired
    private ReserveService reserveService;
    @Autowired
    private DepositRefundService depositRefundService;

    @Value("${library.borrow.max-count:5}")
    private int maxCount;
    @Value("${library.borrow.days:30}")
    private int borrowDays;
    @Value("${library.borrow.overdue-fee-per-day:1}")
    private BigDecimal overdueFeePerDay;
    @Value("${library.borrow.buyout-overdue-days:30}")
    private int buyoutOverdueDays;
    @Value("${library.borrow.pending-expire-hours:2}")
    private int pendingExpireHours;

    // ==================== 申请借阅 ====================

    @Override
    @Transactional
    public PayVO apply(Long bookId) {
        if (bookId == null) {
            throw new BusinessException("请选择要借阅的图书");
        }
        Long userId = UserContext.getUserId();
        // 事务内第一条 SQL 锁住用户行，把「重复借阅 / 借阅上限」这类
        // 先 count 再写入的校验串行化，否则并发请求会各自读到旧快照全部通过
        if (userMapper.lockById(userId) == null) {
            throw new BusinessException("用户不存在");
        }
        User user = userMapper.selectById(userId);
        if (user.getStatus() != null && user.getStatus() == Constants.USER_STATUS_DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
        // 掏钱之前必须已知晓规则，且后端强制校验，不依赖前端弹窗
        agreementService.assertAgreed(userId, Constants.AGREEMENT_TYPE_BORROW);

        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("图书不存在");
        }
        if (book.getPrice() == null || book.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("该图书未设置定价，暂不可借阅");
        }

        // 同一本书不允许同时有两笔未完成的借阅（待取书 / 借阅中）
        Long sameBook = borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getUserId, userId)
                .eq(Borrow::getBookId, bookId)
                .in(Borrow::getStatus,
                        Constants.BORROW_STATUS_PENDING_PICKUP, Constants.BORROW_STATUS_BORROWING));
        if (sameBook != null && sameBook > 0) {
            throw new BusinessException("您已借阅或预订了这本书，请勿重复申请");
        }

        // 借阅上限：待取书与借阅中都算占用额度
        Long borrowing = borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getUserId, userId)
                .in(Borrow::getStatus,
                        Constants.BORROW_STATUS_PENDING_PICKUP, Constants.BORROW_STATUS_BORROWING));
        if (borrowing != null && borrowing >= maxCount) {
            throw new BusinessException("已达借阅上限（" + maxCount + " 本），请先归还部分图书");
        }

        // 预约优先权：规则细节见 ReserveService.assertBorrowAllowed。
        // 抽到公共方法是为了让购物车合并结算走同一套校验，否则能绕过预约把书抢走
        reserveService.assertBorrowAllowed(userId, bookId,
                book.getStock() == null ? 0 : book.getStock());

        // 取一册在馆副本
        List<BookItem> available = bookItemMapper.selectList(new LambdaQueryWrapper<BookItem>()
                .eq(BookItem::getBookId, bookId)
                .eq(BookItem::getStatus, Constants.ITEM_STATUS_IN_LIBRARY)
                .orderByAsc(BookItem::getBarcode)
                .last("LIMIT 1"));
        if (available.isEmpty()) {
            throw new BusinessException("该书已全部借出，可提交预约排队");
        }
        BookItem item = available.get(0);

        // 锁定副本：在馆 -> 已预留。条件更新保证多人抢同一册时只有一个成功
        if (bookItemMapper.changeStatus(item.getId(),
                Constants.ITEM_STATUS_IN_LIBRARY, Constants.ITEM_STATUS_RESERVED) == 0) {
            throw new BusinessException("该册已被他人抢先预留，请重试");
        }

        // 押金 = 该书定价，直接覆盖书价，逾期买断时商户不亏
        BigDecimal deposit = book.getPrice();

        LocalDateTime now = LocalDateTime.now();
        Borrow borrow = new Borrow();
        borrow.setUserId(userId);
        borrow.setBookId(bookId);
        borrow.setItemId(item.getId());
        borrow.setBorrowTime(now);
        // 应还时间先占位，真正的借期从核销时刻起算
        borrow.setDeadlineTime(now.plusDays(borrowDays));
        borrow.setDepositAmount(deposit);
        borrow.setDeductAmount(BigDecimal.ZERO);
        borrow.setRefundAmount(BigDecimal.ZERO);
        borrow.setStatus(Constants.BORROW_STATUS_PENDING_PICKUP);
        borrow.setIsOverdue(0);
        borrow.setCreateTime(now);
        borrow.setUpdateTime(now);
        borrowMapper.insert(borrow);

        PayOrder payOrder = payService.createOrder(userId, Constants.PAY_BIZ_DEPOSIT,
                borrow.getId(), deposit);
        borrow.setPayOrderNo(payOrder.getOrderNo());
        borrowMapper.updateById(borrow);

        bookItemMapper.refreshStock(bookId);

        PayVO vo = new PayVO();
        vo.setOrderNo(payOrder.getOrderNo());
        vo.setAmount(deposit);
        vo.setStatus(payOrder.getStatus());
        vo.setExpireTime(payOrder.getExpireTime());
        vo.setPayForm(payService.buildPayForm(payOrder.getOrderNo()));
        return vo;
    }

    // ==================== 借出核销 ====================

    @Override
    @Transactional
    public BorrowSettleVO checkOut(String barcode, String readerAccount) {
        if (!StringUtils.hasText(barcode)) {
            throw new BusinessException("请扫描或输入副本条码");
        }
        if (!StringUtils.hasText(readerAccount)) {
            throw new BusinessException("请扫描或输入读者账号");
        }

        // 校验 1：副本存在且处于「已预留」
        BookItem item = findByBarcode(barcode.trim());
        if (item == null) {
            throw new BusinessException("副本不存在，请检查条码");
        }
        if (item.getStatus() != Constants.ITEM_STATUS_RESERVED) {
            throw new BusinessException("该副本当前状态为「" + itemStatusText(item.getStatus())
                    + "」，不是待核销的预留状态");
        }

        // 校验 2：读者存在且未禁用
        User reader = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getAccount, readerAccount.trim()));
        if (reader == null) {
            throw new BusinessException("读者不存在，请检查账号");
        }
        if (reader.getStatus() != null && reader.getStatus() == Constants.USER_STATUS_DISABLED) {
            throw new BusinessException("该读者账号已被禁用");
        }

        // 校验 3（最关键）：该读者确实有这本书的待取书单。
        // 没有这一步，任何人拿任何一本书都能核销成功，扫码就形同虚设。
        Borrow borrow = borrowMapper.selectOne(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getUserId, reader.getId())
                .eq(Borrow::getBookId, item.getBookId())
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_PENDING_PICKUP)
                .orderByDesc(Borrow::getId)
                .last("LIMIT 1"));
        if (borrow == null) {
            throw new BusinessException("该读者没有这本书的待取书借阅单");
        }
        if (borrow.getItemId() != null && !borrow.getItemId().equals(item.getId())) {
            throw new BusinessException("该借阅单预留的不是这一册，请核对条码");
        }

        // 校验 4：押金必须已支付（部分退款也算付过）
        // 合并单里别的书退过款后支付单会变成 4，但本单押金仍扣在那儿，不能因此卡住核销
        PayOrder payOrder = borrow.getPayOrderNo() == null ? null
                : payService.getByOrderNo(borrow.getPayOrderNo());
        if (payOrder == null
                || (payOrder.getStatus() != Constants.PAY_STATUS_PAID
                && payOrder.getStatus() != Constants.PAY_STATUS_PARTIAL_REFUNDED)) {
            throw new BusinessException("该借阅单押金尚未支付，无法核销");
        }

        // 校验 5：在借数量未超上限
        Long borrowing = borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getUserId, reader.getId())
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_BORROWING));
        if (borrowing != null && borrowing >= maxCount) {
            throw new BusinessException("该读者借阅数量已达上限（" + maxCount + " 本）");
        }

        // 原子切换副本：已预留 -> 借出中
        if (bookItemMapper.changeStatus(item.getId(),
                Constants.ITEM_STATUS_RESERVED, Constants.ITEM_STATUS_BORROWED) == 0) {
            throw new BusinessException("核销失败，该副本状态已被其他操作改变");
        }

        LocalDateTime now = LocalDateTime.now();
        // 借期从核销这一刻起算，而不是从下单那一刻
        borrow.setStatus(Constants.BORROW_STATUS_BORROWING);
        borrow.setVerifyTime(now);
        borrow.setDeadlineTime(now.plusDays(borrowDays));
        borrow.setUpdateTime(now);
        borrowMapper.updateById(borrow);

        bookItemMapper.refreshStock(item.getBookId());

        // 该读者若有这本书的「待借阅」预约，一并置为已借阅，避免长期占位
        settleReserve(reader.getId(), item.getBookId());

        Book book = bookMapper.selectById(item.getBookId());
        BorrowSettleVO vo = buildSettleVO(borrow, book, item, reader, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        log.info("借出核销成功 borrowId={} barcode={} reader={}",
                borrow.getId(), item.getBarcode(), reader.getAccount());
        return vo;
    }

    @Override
    @Transactional
    public BorrowSettleVO checkOutById(Long borrowId) {
        if (borrowId == null) {
            throw new BusinessException("请选择要核销的借阅单");
        }
        Borrow borrow = borrowMapper.selectById(borrowId);
        if (borrow == null) {
            throw new BusinessException("借阅单不存在");
        }
        if (borrow.getStatus() != Constants.BORROW_STATUS_PENDING_PICKUP) {
            throw new BusinessException("该借阅单不在待取书状态");
        }
        // 押金已支付即可（部分退款也算付过，见 checkOut 的说明）
        PayOrder payOrder = borrow.getPayOrderNo() == null ? null
                : payService.getByOrderNo(borrow.getPayOrderNo());
        if (payOrder == null
                || (payOrder.getStatus() != Constants.PAY_STATUS_PAID
                && payOrder.getStatus() != Constants.PAY_STATUS_PARTIAL_REFUNDED)) {
            throw new BusinessException("该借阅单押金尚未支付，无法核销");
        }
        if (borrow.getItemId() == null) {
            throw new BusinessException("该借阅单没有关联副本，无法核销");
        }
        BookItem item = bookItemMapper.selectById(borrow.getItemId());
        if (item == null) {
            throw new BusinessException("副本不存在");
        }
        if (item.getStatus() != Constants.ITEM_STATUS_RESERVED) {
            throw new BusinessException("副本「" + item.getBarcode() + "」当前状态为「"
                    + itemStatusText(item.getStatus()) + "」，不是待核销状态");
        }
        User reader = userMapper.selectById(borrow.getUserId());
        if (reader == null
                || (reader.getStatus() != null && reader.getStatus() == Constants.USER_STATUS_DISABLED)) {
            throw new BusinessException("该读者账号已被禁用");
        }
        Long borrowing = borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getUserId, borrow.getUserId())
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_BORROWING));
        if (borrowing != null && borrowing >= maxCount) {
            throw new BusinessException("该读者借阅数量已达上限（" + maxCount + " 本）");
        }

        if (bookItemMapper.changeStatus(item.getId(),
                Constants.ITEM_STATUS_RESERVED, Constants.ITEM_STATUS_BORROWED) == 0) {
            throw new BusinessException("核销失败，该副本状态已被其他操作改变");
        }
        LocalDateTime now = LocalDateTime.now();
        borrow.setStatus(Constants.BORROW_STATUS_BORROWING);
        borrow.setVerifyTime(now);
        borrow.setDeadlineTime(now.plusDays(borrowDays));
        borrow.setUpdateTime(now);
        borrowMapper.updateById(borrow);

        bookItemMapper.refreshStock(item.getBookId());
        settleReserve(borrow.getUserId(), item.getBookId());

        Book book = bookMapper.selectById(item.getBookId());
        log.info("借出核销成功（按单） borrowId={} barcode={}", borrow.getId(), item.getBarcode());
        return buildSettleVO(borrow, book, item, reader, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    // ==================== 归还核销 ====================

    @Override
    @Transactional
    public BorrowSettleVO checkIn(String barcode, BigDecimal damageFee) {
        if (!StringUtils.hasText(barcode)) {
            throw new BusinessException("请扫描或输入副本条码");
        }
        BookItem item = findByBarcode(barcode.trim());
        if (item == null) {
            throw new BusinessException("副本不存在，请检查条码");
        }
        if (item.getStatus() != Constants.ITEM_STATUS_BORROWED) {
            throw new BusinessException("该副本当前状态为「" + itemStatusText(item.getStatus())
                    + "」，不在借出状态");
        }

        // 还书只需扫书码：从副本反查借阅单，自动识别是谁借的
        Borrow borrow = borrowMapper.selectOne(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getItemId, item.getId())
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_BORROWING)
                .orderByDesc(Borrow::getId)
                .last("LIMIT 1"));
        if (borrow == null) {
            throw new BusinessException("未找到该副本的借阅记录，请检查条码");
        }
        User reader = userMapper.selectById(borrow.getUserId());

        LocalDateTime now = LocalDateTime.now();
        // 逾期天数：超过应还时间即算逾期，不足一天按一天计
        long overdueDays = 0;
        if (now.isAfter(borrow.getDeadlineTime())) {
            overdueDays = ChronoUnit.DAYS.between(borrow.getDeadlineTime(), now);
            if (overdueDays == 0) {
                overdueDays = 1;
            }
        }
        BigDecimal overdueFee = overdueFeePerDay.multiply(BigDecimal.valueOf(overdueDays));
        BigDecimal damage = damageFee == null || damageFee.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO : damageFee;

        // 扣款封顶：无论逾期多久、破损多严重，最多扣光押金，绝不出现负数
        BigDecimal deposit = borrow.getDepositAmount() == null ? BigDecimal.ZERO : borrow.getDepositAmount();
        BigDecimal totalDeduct = overdueFee.add(damage);
        if (totalDeduct.compareTo(deposit) > 0) {
            totalDeduct = deposit;
        }
        BigDecimal refund = deposit.subtract(totalDeduct);

        // 归还优先：先落库、先回馆，再退款。
        // 如果把退款放在前面且失败就回滚，渠道一故障读者就永远还不成书。
        // 状态先记「待退」：钱还没退出去，就不能显示成已退；
        // 事务提交后才真正调渠道，见 refundDepositAfterCommit 的说明。
        borrow.setStatus(Constants.BORROW_STATUS_RETURNED);
        borrow.setReturnTime(now);
        borrow.setDeductAmount(totalDeduct);
        borrow.setRefundAmount(refund);
        borrow.setIsOverdue(overdueDays > 0 ? 1 : 0);
        borrow.setRefundStatus(refund.compareTo(BigDecimal.ZERO) > 0
                ? Constants.REFUND_STATUS_FAILED : Constants.REFUND_STATUS_NONE);
        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            // 记下「从什么时候开始等」：滞留检测用它算时长，
            // 而不是用 update_time（那会被后续任何无关更新推后）
            borrow.setRefundFailTime(now);
        }
        borrow.setUpdateTime(now);
        borrowMapper.updateById(borrow);

        if (bookItemMapper.changeStatus(item.getId(),
                Constants.ITEM_STATUS_BORROWED, Constants.ITEM_STATUS_IN_LIBRARY) == 0) {
            throw new BusinessException("归还失败，该副本状态已被其他操作改变");
        }
        bookItemMapper.refreshStock(item.getBookId());

        // 扣款流水：必须记「实扣」而不是「应收」。
        // totalDeduct 已被押金封顶，若按 overdueFee 原值记账，账面金额会大于真实扣款，对账时虚高。
        // 分摊原则：优先从押金中扣逾期费（必然发生），剩余额度再给破损赔偿（可能有争议）。
        BigDecimal actualOverdueFee = overdueFee.min(totalDeduct);
        BigDecimal actualDamageFee = totalDeduct.subtract(actualOverdueFee);
        if (actualOverdueFee.compareTo(BigDecimal.ZERO) > 0) {
            String note = overdueFee.compareTo(actualOverdueFee) > 0
                    ? "逾期 " + overdueDays + " 天（应收 " + overdueFee.toPlainString() + "，押金封顶）"
                    : "逾期 " + overdueDays + " 天扣款";
            paymentFlowService.record(borrow.getUserId(), Constants.FLOW_TYPE_OVERDUE_DEDUCT,
                    borrow.getId(), borrow.getPayOrderNo(), Constants.FLOW_DIRECTION_IN,
                    actualOverdueFee, note);
        }
        if (actualDamageFee.compareTo(BigDecimal.ZERO) > 0) {
            paymentFlowService.record(borrow.getUserId(), Constants.FLOW_TYPE_DAMAGE_DEDUCT,
                    borrow.getId(), borrow.getPayOrderNo(), Constants.FLOW_DIRECTION_IN,
                    actualDamageFee, "破损赔偿");
        }
        // 退款：事务提交后再调渠道，避免一个 HTTP 往返把行锁拖住 1 秒以上
        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            refundDepositAfterCommit(borrow.getId(), borrow.getUserId(),
                    borrow.getPayOrderNo(), refund, "归还押金");
        }

        // 触发预约唤醒：通知排队第一位到馆取书
        reserveService.wakeUpQueue(item.getBookId());

        Book book = bookMapper.selectById(item.getBookId());
        BorrowSettleVO vo = buildSettleVO(borrow, book, item, reader, overdueDays,
                overdueFee, damage, refund);
        log.info("归还核销成功 borrowId={} barcode={} 逾期={}天 扣款={} 退款={}",
                borrow.getId(), item.getBarcode(), overdueDays, totalDeduct, refund);
        return vo;
    }

    // ==================== 取消 ====================

    @Override
    @Transactional
    public void cancel(Long borrowId) {
        Long userId = UserContext.getUserId();
        Borrow borrow = borrowMapper.selectById(borrowId);
        if (borrow == null || !borrow.getUserId().equals(userId)) {
            throw new BusinessException("借阅记录不存在");
        }
        if (borrow.getStatus() != Constants.BORROW_STATUS_PENDING_PICKUP) {
            throw new BusinessException("只有待取书的借阅才能取消");
        }
        doCancel(borrow, "用户取消借阅");
    }

    /**
     * 取消一笔待取书借阅：退款 → 释放副本 → 单据置取消
     */
    private void doCancel(Borrow borrow, String reason) {
        BigDecimal deposit = borrow.getDepositAmount() == null
                ? BigDecimal.ZERO : borrow.getDepositAmount();

        // 幂等闸门：只有仍是待取书才能取消。
        // 这里刻意不写 refund_amount —— 钱退没退要看支付单状态，未支付的单只是关单，
        // 若在这里无条件写「已退押金」，用户会看到一笔从未付过的钱显示成已退。
        int updated = borrowMapper.update(null, new LambdaUpdateWrapper<Borrow>()
                .eq(Borrow::getId, borrow.getId())
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_PENDING_PICKUP)
                .set(Borrow::getStatus, Constants.BORROW_STATUS_CANCELED)
                .set(Borrow::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            return;
        }

        PayOrder payOrder = borrow.getPayOrderNo() == null ? null
                : payService.getByOrderNo(borrow.getPayOrderNo());

        // 已支付(1) 与 部分退款(4) 都要退。
        // 合并单里别的书先退过之后，支付单会变成 4，此时本单押金还没退；
        // 只看 status=1 会静默跳过，钱退不出去、记录却写着已退，是实打实的资损。
        boolean refundable = payOrder != null
                && (payOrder.getStatus() == Constants.PAY_STATUS_PAID
                || payOrder.getStatus() == Constants.PAY_STATUS_PARTIAL_REFUNDED);

        if (refundable && deposit.compareTo(BigDecimal.ZERO) > 0) {
            // 先把「应退」意图落库并标为待重试，事务提交后再调渠道。
            // 顺序反过来（先退款后记账）一旦记账失败，钱退了但系统不知道，补偿任务也捡不回来
            markRefundResult(borrow.getId(), deposit, Constants.REFUND_STATUS_FAILED);
            refundDepositAfterCommit(borrow.getId(), borrow.getUserId(),
                    borrow.getPayOrderNo(), deposit, reason);
        } else if (payOrder != null && payOrder.getStatus() == Constants.PAY_STATUS_UNPAID) {
            // 从未付款，关单即可，不产生任何退款记录
            payService.closeOrder(borrow.getPayOrderNo());
        }

        // 释放副本：已预留 -> 在馆
        if (borrow.getItemId() != null) {
            bookItemMapper.changeStatus(borrow.getItemId(),
                    Constants.ITEM_STATUS_RESERVED, Constants.ITEM_STATUS_IN_LIBRARY);
            bookItemMapper.refreshStock(borrow.getBookId());
            reserveService.wakeUpQueue(borrow.getBookId());
        }
        log.info("借阅取消 borrowId={} reason={} 应退={}", borrow.getId(), reason, deposit);
    }

    /**
     * 把渠道退款推迟到事务提交之后执行。
     * <p>
     * 核销与取消都是事务方法，如果在事务内直接调支付宝：
     * 退款是一次 HTTP 往返（0.7~1.2 秒），期间 borrow / book_item 的行锁一直不放，
     * 管理员连续扫码还书时会串行排队，连接池被拖住。
     * <p>
     * 更关键的是资金安全性：如果退款先成功、事务随后回滚，钱退了出去但本地状态
     * 退回原样，补偿任务扫不到这条记录，也就是「钱没了、系统不知道」。
     * 所以顺序固定为「事务内只落意图 → 提交后调渠道 → 失败留待重试」。
     * <p>
     * 没有活动事务时（定时任务里直接调用、单元测试）退化为同步执行。
     */
    private void refundDepositAfterCommit(Long borrowId, Long userId, String payOrderNo,
                                          BigDecimal amount, String reason) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        depositRefundService.refund(borrowId, userId, payOrderNo, amount, reason);
                    } catch (Exception e) {
                        // 回调里抛异常会中断后续 synchronization 的执行，必须自己吞掉。
                        // 状态仍是「待重试」，补偿任务会兜底
                        log.error("事务提交后押金退款异常 borrowId={}", borrowId, e);
                    }
                }
            });
        } else {
            depositRefundService.refund(borrowId, userId, payOrderNo, amount, reason);
        }
    }

    /**
     * 记录退款结果：金额 + 状态一起写，避免「金额写了状态没写」的中间态。
     * <p>
     * 标记为「失败」时同步写下失败时刻，作为退款滞留的计时起点。
     * 只在这一处写，后续重试失败也不会刷新它——
     * 滞留要衡量的是「这笔钱从第一次没退成功到现在等了多久」，不是「最后一次尝试是什么时候」。
     */
    private void markRefundResult(Long borrowId, BigDecimal amount, int refundStatus) {
        LambdaUpdateWrapper<Borrow> wrapper = new LambdaUpdateWrapper<Borrow>()
                .eq(Borrow::getId, borrowId)
                .set(Borrow::getRefundAmount, amount)
                .set(Borrow::getRefundStatus, refundStatus);
        if (refundStatus == Constants.REFUND_STATUS_FAILED) {
            wrapper.set(Borrow::getRefundFailTime, LocalDateTime.now());
        }
        borrowMapper.update(null, wrapper);
    }

    @Override
    @Transactional
    public void cancelByPayOrder(String payOrderNo) {
        if (!StringUtils.hasText(payOrderNo)) {
            return;
        }
        // 合并订单一个支付单对应多条借阅，逐条取消
        List<Borrow> borrows = borrowMapper.selectList(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getPayOrderNo, payOrderNo)
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_PENDING_PICKUP));
        for (Borrow borrow : borrows) {
            doCancel(borrow, "押金支付超时，借阅已自动取消");
        }
    }

    // ==================== 查询 ====================

    @Override
    public List<BorrowVO> myBorrows(Integer status) {
        return listByUser(UserContext.getUserId(), status);
    }

    @Override
    public List<BorrowVO> listByUser(Long userId, Integer status) {
        LambdaQueryWrapper<Borrow> wrapper = new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getUserId, userId);
        if (status != null) {
            wrapper.eq(Borrow::getStatus, status);
        }
        wrapper.orderByDesc(Borrow::getId);
        return convert(borrowMapper.selectList(wrapper));
    }

    @Override
    public Page<BorrowVO> pageBorrows(Integer page, Integer size, String keyword, Integer status) {
        int pageNo = PageUtil.normalizePage(page);
        int pageSize = PageUtil.normalizeSize(size);

        // 关键词过滤下推到 SQL：旧的「先 like 查出一批 id 再 IN」在数据量上来后
        // 会退化成大 IN + 全表扫描，且 SQL 文本随命中数量膨胀
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        com.baomidou.mybatisplus.core.metadata.IPage<Borrow> borrowPage =
                borrowMapper.pageWithKeyword(new Page<>(pageNo, pageSize), status, kw);

        Page<BorrowVO> voPage = new Page<>(pageNo, pageSize, borrowPage.getTotal());
        voPage.setRecords(convert(borrowPage.getRecords()));
        return voPage;
    }

    // ==================== 定时任务 ====================

    @Override
    @Transactional
    public void processOverdue() {
        LocalDateTime now = LocalDateTime.now();
        List<Borrow> overdueList = borrowMapper.selectList(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_BORROWING)
                .lt(Borrow::getDeadlineTime, now));
        int buyoutCount = 0;
        for (Borrow borrow : overdueList) {
            long overdueDays = ChronoUnit.DAYS.between(borrow.getDeadlineTime(), now);
            if (overdueDays <= 0) {
                continue;
            }
            BigDecimal deposit = borrow.getDepositAmount() == null
                    ? BigDecimal.ZERO : borrow.getDepositAmount();
            // 逾期费按天累计，用于判断押金是否已被扣光
            BigDecimal accruedFee = overdueFeePerDay.multiply(BigDecimal.valueOf(overdueDays));

            // 两个买断触发条件，先到者生效：
            // 1) 逾期满约定期限（默认 30 天）；
            // 2) 累计逾期费 >= 押金——押金已经被扣光，再等下去也收不到更多钱。
            //    少了第 2 条会出现这种荒唐情形：20 元的书逾期 20 天押金已经扣尽，
            //    系统却要等到第 30 天才买断；用户第 25 天来还书，扣满 20 元、退 0 元，
            //    书却被收回馆藏，等于付了全价还没拿到书。
            boolean timeUp = overdueDays >= buyoutOverdueDays;
            boolean depositExhausted = deposit.compareTo(BigDecimal.ZERO) > 0
                    && accruedFee.compareTo(deposit) >= 0;
            if (!timeUp && !depositExhausted) {
                continue;
            }
            if (doBuyout(borrow, depositExhausted && !timeUp)) {
                buyoutCount++;
            }
        }
        if (buyoutCount > 0) {
            log.info("逾期买断处理完成，本次买断 {} 笔", buyoutCount);
        }
    }

    /**
     * 逾期买断：押金全部没收，书归用户
     * <p>
     * 幂等靠条件更新：只有仍是「借阅中」的才能改成「已买断」。
     *
     * @param feeExhausted true 表示因「押金被扣光」买断，而不是「逾期满期」
     */
    private boolean doBuyout(Borrow borrow, boolean feeExhausted) {
        BigDecimal deposit = borrow.getDepositAmount() == null ? BigDecimal.ZERO : borrow.getDepositAmount();
        int updated = borrowMapper.update(null, new LambdaUpdateWrapper<Borrow>()
                .eq(Borrow::getId, borrow.getId())
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_BORROWING)
                .set(Borrow::getStatus, Constants.BORROW_STATUS_BOUGHT_OUT)
                .set(Borrow::getIsOverdue, 1)
                .set(Borrow::getDeductAmount, deposit)
                .set(Borrow::getRefundAmount, BigDecimal.ZERO)
                .set(Borrow::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            return false;
        }

        if (borrow.getItemId() != null) {
            bookItemMapper.changeStatus(borrow.getItemId(),
                    Constants.ITEM_STATUS_BORROWED, Constants.ITEM_STATUS_BOUGHT_OUT);
            bookItemMapper.refreshStock(borrow.getBookId());
        }
        if (deposit.compareTo(BigDecimal.ZERO) > 0) {
            String note = feeExhausted
                    ? "逾期费已达押金上限，视为购买，押金不退"
                    : "逾期满 " + buyoutOverdueDays + " 天，视为购买，押金不退";
            paymentFlowService.record(borrow.getUserId(), Constants.FLOW_TYPE_BUYOUT_DEDUCT,
                    borrow.getId(), borrow.getPayOrderNo(), Constants.FLOW_DIRECTION_IN,
                    deposit, note);
        }
        log.info("逾期买断 borrowId={} userId={} 押金={} 原因={}",
                borrow.getId(), borrow.getUserId(), deposit, feeExhausted ? "押金扣光" : "逾期满期");
        return true;
    }

    @Override
    @Transactional
    public void cancelExpiredPending() {
        // 时限从「支付成功时刻」起算（pay_time），而不是下单时刻（borrow_time）。
        // 用 borrow_time 会让晚付款的用户取书窗口被凭空压缩，与规则文案不符。
        LocalDateTime threshold = LocalDateTime.now().minusHours(pendingExpireHours);
        List<Borrow> pendingList = borrowMapper.selectPickupTimeout(threshold);
        for (Borrow borrow : pendingList) {
            try {
                doCancel(borrow, "超过 " + pendingExpireHours + " 小时未到馆取书，自动取消");
            } catch (Exception e) {
                log.error("自动取消待取书借阅失败 borrowId={}", borrow.getId(), e);
            }
        }
        if (!pendingList.isEmpty()) {
            log.info("超时未取书处理完成，本次处理 {} 笔", pendingList.size());
        }
    }

    @Override
    @Transactional
    public void retryFailedRefunds() {
        // 归还失败、取消失败都要补偿：取消路径同样可能因渠道故障没退成
        List<Borrow> list = borrowMapper.selectList(new LambdaQueryWrapper<Borrow>()
                .in(Borrow::getStatus,
                        Constants.BORROW_STATUS_RETURNED, Constants.BORROW_STATUS_CANCELED)
                .eq(Borrow::getRefundStatus, Constants.REFUND_STATUS_FAILED));
        for (Borrow b : list) {
            BigDecimal refund = b.getRefundAmount() == null ? BigDecimal.ZERO : b.getRefundAmount();
            if (refund.compareTo(BigDecimal.ZERO) <= 0) {
                markRefundStatus(b.getId(), Constants.REFUND_STATUS_SUCCESS);
                continue;
            }
            PayOrder pay = b.getPayOrderNo() == null ? null : payService.getByOrderNo(b.getPayOrderNo());
            // 支付单已全额退回：钱已经在渠道侧退出去了，继续调只会一直失败。
            // 这种情况多半是同一支付单下多笔业务先后退款，钱已退完但本单标记没跟上
            if (pay != null && pay.getStatus() == Constants.PAY_STATUS_REFUNDED) {
                log.warn("支付单已全额退款，跳过重试并标记完成 borrowId={} payOrderNo={}",
                        b.getId(), b.getPayOrderNo());
                markRefundStatus(b.getId(), Constants.REFUND_STATUS_SUCCESS);
                continue;
            }
            // 统一走 DepositRefundService：那里有「是否已有退款流水」的幂等闸门。
            // 补偿任务如果自己直接调渠道，就绕过了这道闸门——
            // 状态位被回滚/回写失败时，累计退款金额会被重复累加，账面虚高。
            // 单条失败必须自己接住：DepositRefundService 现在会把异常抛出（让它的事务干净回滚），
            // 不 catch 的话第一条失败就会中断整批补偿
            try {
                depositRefundService.refund(b.getId(), b.getUserId(), b.getPayOrderNo(),
                        refund, "押金退还（补偿退款）");
            } catch (Exception e) {
                log.error("补偿退款仍失败，等待下次重试 borrowId={}", b.getId(), e);
            }
        }
    }

    private void markRefundStatus(Long borrowId, int status) {
        borrowMapper.update(null, new LambdaUpdateWrapper<Borrow>()
                .eq(Borrow::getId, borrowId)
                .set(Borrow::getRefundStatus, status));
    }

    // ==================== 私有方法 ====================

    private BookItem findByBarcode(String barcode) {
        return bookItemMapper.selectOne(new LambdaQueryWrapper<BookItem>()
                .eq(BookItem::getBarcode, barcode));
    }

    /**
     * 借出成功后，把该读者对这本书还没结束的预约（排队中 / 待借阅）置为已借阅。
     * <p>
     * 只结算「待借阅」是不够的：读者可能在排队期间直接从柜台借到了这本书
     * （库存回补后还没来得及叫号），此时那条「排队中」已经兑现，
     * 留着它既让读者以为自己还在等，也会在下次补货时把通知发给已经拿到书的人。
     */
    private void settleReserve(Long userId, Long bookId) {
        List<BookReserve> list = bookReserveMapper.selectList(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getUserId, userId)
                .eq(BookReserve::getBookId, bookId)
                .in(BookReserve::getStatus,
                        Constants.RESERVE_STATUS_QUEUING, Constants.RESERVE_STATUS_NOTIFIED));
        for (BookReserve reserve : list) {
            reserve.setStatus(Constants.RESERVE_STATUS_BORROWED);
            bookReserveMapper.updateById(reserve);
        }
    }

    private BorrowSettleVO buildSettleVO(Borrow borrow, Book book, BookItem item, User reader,
                                         long overdueDays, BigDecimal overdueFee,
                                         BigDecimal damageFee, BigDecimal refund) {
        BorrowSettleVO vo = new BorrowSettleVO();
        vo.setBorrowId(borrow.getId());
        vo.setBookName(book == null ? "（图书已下架）" : book.getName());
        vo.setBarcode(item == null ? null : item.getBarcode());
        vo.setUserAccount(reader == null ? null : reader.getAccount());
        vo.setUserName(reader == null ? null : reader.getName());
        vo.setDepositAmount(borrow.getDepositAmount());
        vo.setDeductAmount(borrow.getDeductAmount());
        vo.setRefundAmount(refund);
        vo.setOverdueDays(overdueDays);
        vo.setOverdueFee(overdueFee);
        vo.setDamageFee(damageFee);
        vo.setStatus(borrow.getStatus());
        vo.setStatusText(borrowStatusText(borrow.getStatus()));
        return vo;
    }

    private List<BorrowVO> convert(List<Borrow> borrows) {
        if (borrows == null || borrows.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> userIds = borrows.stream().map(Borrow::getUserId).distinct().collect(Collectors.toList());
        List<Long> bookIds = borrows.stream().map(Borrow::getBookId).distinct().collect(Collectors.toList());
        List<Long> itemIds = borrows.stream().map(Borrow::getItemId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        List<String> payOrderNos = borrows.stream().map(Borrow::getPayOrderNo)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());

        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Book> bookMap = bookMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, b -> b));
        Map<Long, BookItem> itemMap = itemIds.isEmpty() ? java.util.Collections.emptyMap()
                : bookItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(BookItem::getId, i -> i));
        Map<String, PayOrder> payMap = payOrderNos.isEmpty() ? java.util.Collections.emptyMap()
                : payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                        .in(PayOrder::getOrderNo, payOrderNos)).stream()
                .collect(Collectors.toMap(PayOrder::getOrderNo, p -> p));

        LocalDateTime now = LocalDateTime.now();
        return borrows.stream().map(b -> {
            BorrowVO vo = new BorrowVO();
            vo.setId(b.getId());
            vo.setUserId(b.getUserId());
            vo.setBookId(b.getBookId());
            vo.setItemId(b.getItemId());
            vo.setBorrowTime(b.getBorrowTime());
            vo.setVerifyTime(b.getVerifyTime());
            vo.setDeadlineTime(b.getDeadlineTime());
            vo.setReturnTime(b.getReturnTime());
            vo.setDepositAmount(b.getDepositAmount());
            vo.setDeductAmount(b.getDeductAmount());
            vo.setRefundAmount(b.getRefundAmount());
            vo.setRefundStatus(b.getRefundStatus());
            vo.setPayOrderNo(b.getPayOrderNo());
            vo.setPickupCode(b.getPickupCode());
            vo.setStatus(b.getStatus());
            vo.setIsOverdue(b.getIsOverdue());

            User u = userMap.get(b.getUserId());
            if (u != null) {
                vo.setUserAccount(u.getAccount());
                vo.setUserName(u.getName());
            }
            Book book = bookMap.get(b.getBookId());
            if (book != null) {
                vo.setBookName(book.getName());
                vo.setBookCover(book.getCover());
            } else {
                vo.setBookName("（图书已下架）");
            }
            if (b.getItemId() != null) {
                BookItem item = itemMap.get(b.getItemId());
                if (item != null) {
                    vo.setBarcode(item.getBarcode());
                }
            }
            if (b.getPayOrderNo() != null) {
                PayOrder pay = payMap.get(b.getPayOrderNo());
                if (pay != null) {
                    vo.setPayStatus(pay.getStatus());
                }
            }
            // 状态文案：待取书但押金还没付时显示「待支付」。
            // 否则用户看到「待取书」会直接跑去馆里，而管理员核销时会因押金未付被拒
            String statusText = borrowStatusText(b.getStatus());
            if (b.getStatus() == Constants.BORROW_STATUS_PENDING_PICKUP
                    && vo.getPayStatus() != null
                    && vo.getPayStatus() == Constants.PAY_STATUS_UNPAID) {
                statusText = "待支付";
            }
            vo.setStatusText(statusText);
            // 剩余天数：已核销且在借中才有意义
            if (b.getStatus() == Constants.BORROW_STATUS_BORROWING && b.getDeadlineTime() != null) {
                vo.setRemainDays(ChronoUnit.DAYS.between(now, b.getDeadlineTime()));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private String borrowStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case Constants.BORROW_STATUS_PENDING_PICKUP:
                return "待取书";
            case Constants.BORROW_STATUS_BORROWING:
                return "借阅中";
            case Constants.BORROW_STATUS_RETURNED:
                return "已归还";
            case Constants.BORROW_STATUS_BOUGHT_OUT:
                return "已买断";
            case Constants.BORROW_STATUS_CANCELED:
                return "已取消";
            default:
                return "未知";
        }
    }

    private String itemStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case Constants.ITEM_STATUS_IN_LIBRARY:
                return "在馆";
            case Constants.ITEM_STATUS_BORROWED:
                return "借出中";
            case Constants.ITEM_STATUS_SOLD:
                return "已售出";
            case Constants.ITEM_STATUS_BOUGHT_OUT:
                return "已买断";
            case Constants.ITEM_STATUS_LOST:
                return "遗失";
            case Constants.ITEM_STATUS_DAMAGED:
                return "破损";
            case Constants.ITEM_STATUS_OFFLINE:
                return "下架";
            case Constants.ITEM_STATUS_RESERVED:
                return "已预留";
            default:
                return "未知";
        }
    }
}
