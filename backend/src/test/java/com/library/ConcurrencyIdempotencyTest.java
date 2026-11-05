package com.library;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.common.Constants;
import com.library.entity.Borrow;
import com.library.entity.BookItem;
import com.library.entity.PayOrder;
import com.library.entity.ReconcileRecord;
import com.library.mapper.BookItemMapper;
import com.library.mapper.BorrowMapper;
import com.library.mapper.PayOrderMapper;
import com.library.service.AgreementService;
import com.library.service.BorrowService;
import com.library.service.PayService;
import com.library.service.PaySettleHandler;
import com.library.service.ReconcileService;
import com.library.util.UserContext;
import com.library.vo.ReconcileResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发与幂等的集成测试。
 * <p>
 * 这套系统的「不坏」几乎全靠几处条件更新 SQL：
 * <ul>
 *   <li>{@code UPDATE book_item SET status=? WHERE id=? AND status=?}</li>
 *   <li>{@code UPDATE pay_order SET status=1 WHERE order_no=? AND status=0}</li>
 *   <li>{@code UPDATE pay_order SET refund_amount=refund_amount+? WHERE ... <= amount}</li>
 * </ul>
 * 这些设计只有在并发下被真的打一遍才叫验证过。人工点页面永远点不出竞态。
 * <p>
 * 运行方式（需要本地 MySQL 且有 library_test 库）：
 * <pre>
 * mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS library_test ..."
 * mvn test -Pit
 * </pre>
 */
@Tag("it")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/library_test"
                + "?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
        "library.pay.alipay.mock-enabled=true"
})
class ConcurrencyIdempotencyTest {

    /** 《活着》定价 20 元，正好用来验证「逾期费扣光押金即买断」 */
    private static final long BOOK_ID = 4L;
    private static final long ZHANG = 2L;
    private static final long LI = 3L;

    @Autowired
    private BorrowService borrowService;
    @Autowired
    private PayService payService;
    @Autowired
    private PaySettleHandler paySettleHandler;
    @Autowired
    private ReconcileService reconcileService;
    @Autowired
    private AgreementService agreementService;
    @Autowired
    private BookItemMapper bookItemMapper;
    @Autowired
    private BorrowMapper borrowMapper;
    @Autowired
    private PayOrderMapper payOrderMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        UserContext.clear();
        jdbc.update("DELETE FROM borrow");
        jdbc.update("DELETE FROM pay_order");
        jdbc.update("DELETE FROM payment_flow");
        jdbc.update("DELETE FROM book_reserve");
        jdbc.update("DELETE FROM message");
        jdbc.update("DELETE FROM reconcile_record");
        jdbc.update("DELETE FROM book_item WHERE book_id = ?", BOOK_ID);
        jdbc.update("UPDATE book SET stock = 0 WHERE id = ?", BOOK_ID);
        // 协议必须在掏钱之前签好，否则 apply 会被业务入口挡住
        for (long uid : new long[]{ZHANG, LI}) {
            agreementService.agree(uid, Constants.AGREEMENT_TYPE_BORROW, "127.0.0.1");
        }
    }

    // ==================== 1. 并发抢同一册 ====================

    @Test
    @DisplayName("两人同时借仅剩 1 册的书：只有 1 人成功，副本只被预留 1 次")
    void concurrentBorrowSameItem() throws Exception {
        jdbc.update("INSERT INTO book_item (book_id, barcode, status, is_delete, create_time, update_time) "
                + "VALUES (?, 'TEST-BORROW-1', 0, 0, NOW(), NOW())", BOOK_ID);
        jdbc.update("UPDATE book SET stock = 1 WHERE id = ?", BOOK_ID);

        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());

        long[] users = {ZHANG, LI};
        for (long uid : users) {
            new Thread(() -> {
                UserContext.set(uid, "u" + uid, Constants.ROLE_READER);
                try {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    borrowService.apply(BOOK_ID);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    errors.add(e.getMessage());
                } finally {
                    UserContext.clear();
                    done.countDown();
                }
            }).start();
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS), "线程未就绪");
        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS), "并发请求未在超时内结束");

        assertEquals(1, success.get(), "只应有一个线程借阅成功，实际成功 " + success.get()
                + "，失败原因=" + errors);
        assertEquals(1, failed.get(), "另一个线程必须失败，否则同一册被重复预留");

        Long reserved = bookItemMapper.selectCount(new LambdaQueryWrapper<BookItem>()
                .eq(BookItem::getBookId, BOOK_ID)
                .eq(BookItem::getStatus, Constants.ITEM_STATUS_RESERVED));
        assertEquals(1L, reserved, "副本只应有一条处于「已预留」");
    }

    // ==================== 2. 支付回调重复推送 ====================

    @Test
    @DisplayName("同一支付单并发入账 10 次：只入账 1 次，资金流水只多 1 条")
    void duplicatePayNotifyOnlySettlesOnce() throws Exception {
        PayOrder order = payService.createOrder(ZHANG, Constants.PAY_BIZ_DEPOSIT,
                1L, new BigDecimal("20.00"));
        long flowBefore = countFlows(order.getOrderNo());

        int threads = 10;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger settled = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    // 模拟同一笔交易的回调被重复推送 / 回调与查单补偿撞车
                    if (paySettleHandler.settle(order, "TRADE-" + idx)) {
                        settled.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // 并发下部分线程因行锁冲突抛异常属正常，只要最终只入账一次
                } finally {
                    done.countDown();
                }
            }).start();
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS));

        assertEquals(1, settled.get(), "重复回调只允许入账一次，实际 " + settled.get());

        PayOrder after = payOrderMapper.selectById(order.getId());
        assertEquals(Constants.PAY_STATUS_PAID, after.getStatus(), "支付单应为已支付");
        assertEquals(flowBefore + 1, countFlows(order.getOrderNo()),
                "资金流水只应新增 1 条，重复入账会造成账实不符");
    }

    // ==================== 3. 退款补偿重复执行 ====================

    @Test
    @DisplayName("退款补偿重复触发：累计退款金额不翻倍")
    void repeatedRefundCompensationIsIdempotent() {
        PayOrder pay = payService.createOrder(ZHANG, Constants.PAY_BIZ_DEPOSIT,
                99L, new BigDecimal("20.00"));
        // 手动置为已支付
        jdbc.update("UPDATE pay_order SET status = 1, pay_time = NOW(), trade_no = 'T-REFUND' "
                + "WHERE id = ?", pay.getId());

        // 构造一笔「已归还、有 10 元待退、上次退款失败」的借阅
        jdbc.update("INSERT INTO borrow (user_id, book_id, borrow_time, verify_time, deadline_time, "
                        + "deposit_amount, deduct_amount, refund_amount, refund_status, pay_order_no, "
                        + "status, is_overdue, create_time, update_time) "
                        + "VALUES (?, ?, NOW(), NOW(), NOW(), 20.00, 10.00, 10.00, 2, ?, 2, 0, NOW(), NOW())",
                ZHANG, BOOK_ID, pay.getOrderNo());
        Long borrowId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 第一次补偿：应退成功，累计退款 10
        borrowService.retryFailedRefunds();
        BigDecimal afterFirst = jdbc.queryForObject(
                "SELECT refund_amount FROM pay_order WHERE id = ?", BigDecimal.class, pay.getId());
        assertEquals(0, new BigDecimal("10.00").compareTo(afterFirst),
                "第一次补偿后累计退款应为 10，实际 " + afterFirst);

        // 把状态人为改回「待重试」，模拟「已退成功但状态回写失败」的极端情况，
        // 再触发一次补偿：退款必须仍然只记一次
        jdbc.update("UPDATE borrow SET refund_status = 2 WHERE id = ?", borrowId);
        borrowService.retryFailedRefunds();

        BigDecimal afterSecond = jdbc.queryForObject(
                "SELECT refund_amount FROM pay_order WHERE id = ?", BigDecimal.class, pay.getId());
        assertEquals(0, new BigDecimal("10.00").compareTo(afterSecond),
                "重复补偿不能让累计退款翻倍，实际 " + afterSecond);

        Integer refundStatus = jdbc.queryForObject(
                "SELECT refund_status FROM borrow WHERE id = ?", Integer.class, borrowId);
        assertEquals(Constants.REFUND_STATUS_SUCCESS, refundStatus,
                "有退款流水即视为已退，状态应回到成功");

        Long flowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_flow WHERE biz_type = ? AND biz_id = ?",
                Long.class, Constants.FLOW_TYPE_DEPOSIT_REFUND, borrowId);
        assertEquals(1L, flowCount, "退款流水只应有一条");
    }

    // ==================== 4. 超时未取书释放并唤醒预约 ====================

    @Test
    @DisplayName("支付后超时未取书：自动取消、释放副本、退款，并唤醒预约队列")
    void expiredPickupReleasesItemAndWakesReserve() {
        // 1 册被预留（下单锁定），3 小时前已付款
        jdbc.update("INSERT INTO book_item (book_id, barcode, status, is_delete, create_time, update_time) "
                + "VALUES (?, 'TEST-TIMEOUT-1', 7, 0, NOW(), NOW())", BOOK_ID);
        Long itemId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        PayOrder pay = payService.createOrder(ZHANG, Constants.PAY_BIZ_DEPOSIT,
                100L, new BigDecimal("20.00"));
        // 关键：pay_time 是 3 小时前——超时判定必须看支付时间，不是下单时间
        jdbc.update("UPDATE pay_order SET status = 1, pay_time = DATE_SUB(NOW(), INTERVAL 3 HOUR), "
                + "trade_no = 'T-TIMEOUT' WHERE id = ?", pay.getId());

        jdbc.update("INSERT INTO borrow (user_id, book_id, item_id, borrow_time, deadline_time, "
                        + "deposit_amount, deduct_amount, refund_amount, refund_status, pay_order_no, "
                        + "status, is_overdue, create_time, update_time) "
                        + "VALUES (?, ?, ?, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 20.00, 0, 0, 0, ?, 0, 0, NOW(), NOW())",
                ZHANG, BOOK_ID, itemId, pay.getOrderNo());
        Long borrowId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 另一位读者在排队等这本书
        jdbc.update("INSERT INTO book_reserve (user_id, book_id, status, reserve_time, create_time) "
                + "VALUES (?, ?, 0, NOW(), NOW())", LI, BOOK_ID);

        borrowService.cancelExpiredPending();

        Integer borrowStatus = jdbc.queryForObject(
                "SELECT status FROM borrow WHERE id = ?", Integer.class, borrowId);
        assertEquals(Constants.BORROW_STATUS_CANCELED, borrowStatus, "超时未取书应被取消");

        Integer itemStatus = jdbc.queryForObject(
                "SELECT status FROM book_item WHERE id = ?", Integer.class, itemId);
        assertEquals(Constants.ITEM_STATUS_IN_LIBRARY, itemStatus, "副本必须回到在馆，否则永远借不出去");

        Integer reserveStatus = jdbc.queryForObject(
                "SELECT status FROM book_reserve WHERE book_id = ? AND user_id = ?",
                Integer.class, BOOK_ID, LI);
        assertEquals(Constants.RESERVE_STATUS_NOTIFIED, reserveStatus,
                "书回到在馆后应唤醒排队的第一位读者");
    }

    // ==================== 5. 逾期买断（押金扣光先到） ====================

    @Test
    @DisplayName("逾期费已超过押金：即使未满 30 天也应按买断处理")
    void buyoutWhenDepositExhausted() {
        // 书价 20 元，逾期费 1 元/天，逾期 25 天 → 25 元 > 20 元押金
        jdbc.update("INSERT INTO book_item (book_id, barcode, status, is_delete, create_time, update_time) "
                + "VALUES (?, 'TEST-OVERDUE-1', 1, 0, NOW(), NOW())", BOOK_ID);
        Long itemId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("INSERT INTO borrow (user_id, book_id, item_id, borrow_time, verify_time, deadline_time, "
                        + "deposit_amount, deduct_amount, refund_amount, refund_status, "
                        + "status, is_overdue, create_time, update_time) "
                        + "VALUES (?, ?, ?, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY), "
                        + "DATE_SUB(NOW(), INTERVAL 25 DAY), 20.00, 0, 0, 0, 1, 0, NOW(), NOW())",
                ZHANG, BOOK_ID, itemId);
        Long borrowId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        borrowService.processOverdue();

        Integer status = jdbc.queryForObject(
                "SELECT status FROM borrow WHERE id = ?", Integer.class, borrowId);
        assertEquals(Constants.BORROW_STATUS_BOUGHT_OUT, status,
                "押金已被逾期费扣光，应提前买断，而不是等到第 30 天");

        BigDecimal deduct = jdbc.queryForObject(
                "SELECT deduct_amount FROM borrow WHERE id = ?", BigDecimal.class, borrowId);
        assertEquals(0, new BigDecimal("20.00").compareTo(deduct), "买断应没收全部押金");

        Integer itemStatus = jdbc.queryForObject(
                "SELECT status FROM book_item WHERE id = ?", Integer.class, itemId);
        assertEquals(Constants.ITEM_STATUS_BOUGHT_OUT, itemStatus, "副本应转为已买断，不再参与流通");
    }

    // ==================== 6. 模拟对账不产生假差异 ====================

    @Test
    @DisplayName("已退款订单在模拟对账中不应被判成「渠道无此交易」")
    void simulatedReconcileIgnoresRefundedOrders() {
        // 三笔单：已支付、部分退款、全额退款，都发生在「昨天」
        insertPaidOrder("PO-RECON-1", new BigDecimal("20.00"), Constants.PAY_STATUS_PAID);
        insertPaidOrder("PO-RECON-2", new BigDecimal("30.00"), Constants.PAY_STATUS_PARTIAL_REFUNDED);
        insertPaidOrder("PO-RECON-3", new BigDecimal("40.00"), Constants.PAY_STATUS_REFUNDED);

        ReconcileResultVO result = reconcileService.reconcile(LocalDate.now().minusDays(1));

        assertTrue(Boolean.TRUE.equals(result.getSimulated()), "mock 模式下应为模拟对账");
        assertEquals(3, result.getLocalCount(), "三笔单都应进入本地侧");
        assertEquals(0, result.getDiffCount(),
                "模拟渠道侧由本地数据构造，口径必须与本地侧一致；"
                        + "只取 status=1 会把两笔退款单误判成疑似伪造回调");
    }

    private void insertPaidOrder(String orderNo, BigDecimal amount, int status) {
        jdbc.update("INSERT INTO pay_order (order_no, user_id, biz_type, biz_id, amount, refund_amount, "
                        + "status, channel, trade_no, pay_time, expire_time, is_delete, create_time, update_time) "
                        + "VALUES (?, ?, 1, 1, ?, 0, ?, 1, ?, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0, NOW(), NOW())",
                orderNo, ZHANG, amount, status, "T-" + orderNo);
    }

    // ==================== 7. 退款滞留检测 ====================

    @Test
    @DisplayName("退款失败超过阈值：应在对账页报出「退款滞留」")
    void staleRefundSurfacesInReconcile() {
        // 30 小时前归还、退款标记为失败，补偿任务一直没成功
        jdbc.update("INSERT INTO borrow (user_id, book_id, borrow_time, verify_time, deadline_time, return_time, "
                        + "deposit_amount, deduct_amount, refund_amount, refund_status, pay_order_no, "
                        + "status, is_overdue, create_time, update_time) "
                        + "VALUES (?, ?, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), "
                        + "DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 30 HOUR), "
                        + "20.00, 0, 20.00, 2, 'PO-STALE-1', 2, 0, NOW(), DATE_SUB(NOW(), INTERVAL 30 HOUR))",
                ZHANG, BOOK_ID);

        ReconcileResultVO result = reconcileService.reconcile(LocalDate.now().minusDays(1));

        assertEquals(1, result.getDiffCount(), "应报出 1 条退款滞留");
        Long stale = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reconcile_record WHERE diff_type = ? AND order_no = 'PO-STALE-1'",
                Long.class, ReconcileRecord.DIFF_REFUND_STALE);
        assertEquals(1L, stale, "应写入一条 diff_type=4 的记录");

        // 重跑一次：同一笔不能重复生成
        reconcileService.reconcile(LocalDate.now().minusDays(1));
        Long again = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reconcile_record WHERE diff_type = ? AND order_no = 'PO-STALE-1'",
                Long.class, ReconcileRecord.DIFF_REFUND_STALE);
        assertEquals(1L, again, "同一笔滞留退款不应每天重复堆积");
    }

    @Test
    @DisplayName("合并支付单下两笔借阅同时滞留：应各报一条，不能只报一条")
    void staleRefundReportedPerBorrow() {
        // 购物车合并结算：一个 pay_order 对应两笔借阅，各自的押金独立
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO borrow (user_id, book_id, borrow_time, verify_time, deadline_time, return_time, "
                            + "deposit_amount, deduct_amount, refund_amount, refund_status, refund_fail_time, "
                            + "pay_order_no, status, is_overdue, create_time, update_time) "
                            + "VALUES (?, ?, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), "
                            + "DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 30 HOUR), "
                            + "20.00, 0, 20.00, 2, DATE_SUB(NOW(), INTERVAL 30 HOUR), "
                            + "'PO-COMBINED-1', 2, 0, NOW(), DATE_SUB(NOW(), INTERVAL 30 HOUR))",
                    ZHANG, BOOK_ID);
        }

        reconcileService.reconcile(LocalDate.now().minusDays(1));

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reconcile_record WHERE diff_type = ? AND order_no = 'PO-COMBINED-1'",
                Long.class, ReconcileRecord.DIFF_REFUND_STALE);
        assertEquals(2L, count, "两笔借阅各自滞留，必须各报一条，只按 order_no 去重会漏掉一笔");

        Long distinctBorrow = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT borrow_id) FROM reconcile_record "
                        + "WHERE diff_type = ? AND order_no = 'PO-COMBINED-1'",
                Long.class, ReconcileRecord.DIFF_REFUND_STALE);
        assertEquals(2L, distinctBorrow, "记录应分别关联到不同的 borrow_id");
    }

    @Test
    @DisplayName("计时用 refund_fail_time，不被后续无关更新推后")
    void staleRefundUsesRefundFailTime() {
        // update_time 是 1 小时前（新鲜），但退款其实 30 小时前就失败了：
        // 用 update_time 判断会漏报，必须用 refund_fail_time
        jdbc.update("INSERT INTO borrow (user_id, book_id, borrow_time, verify_time, deadline_time, return_time, "
                        + "deposit_amount, deduct_amount, refund_amount, refund_status, refund_fail_time, "
                        + "pay_order_no, status, is_overdue, create_time, update_time) "
                        + "VALUES (?, ?, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), "
                        + "DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 30 HOUR), "
                        + "20.00, 0, 20.00, 2, DATE_SUB(NOW(), INTERVAL 30 HOUR), "
                        + "'PO-STALE-TIME', 2, 0, NOW(), DATE_SUB(NOW(), INTERVAL 1 HOUR))",
                ZHANG, BOOK_ID);

        reconcileService.reconcile(LocalDate.now().minusDays(1));

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reconcile_record WHERE diff_type = ? AND order_no = 'PO-STALE-TIME'",
                Long.class, ReconcileRecord.DIFF_REFUND_STALE);
        assertEquals(1L, count, "应按 refund_fail_time(30 小时前) 判定滞留，而不是 update_time(1 小时前)");
    }

    // ==================== 辅助 ====================

    private long countFlows(String orderNo) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_flow WHERE biz_no = ?", Long.class, orderNo);
        return n == null ? 0 : n;
    }
}
