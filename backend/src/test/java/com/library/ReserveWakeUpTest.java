package com.library;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.entity.BookReserve;
import com.library.mapper.BookReserveMapper;
import com.library.service.AgreementService;
import com.library.service.BookItemService;
import com.library.service.BorrowService;
import com.library.service.ReserveService;
import com.library.util.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预约队列唤醒与预约优先权的集成测试。
 * <p>
 * 覆盖的是「一次补多册」这个边界：补 N 册只唤醒 1 人时，剩下 N-1 册会空在架子上，
 * 而队列里的人仍被预约优先权挡住——书有货却借不走。
 * <p>
 * 不依赖 HTTP 层，直接调 Service，用 UserContext 模拟登录态，
 * 这样不需要启动 Web 服务、也不需要真实登录凭据。
 */
@Tag("it")
@SpringBootTest(properties = {
        // 与 ConcurrencyIdempotencyTest 共用同一个测试库。
        // 原来指向 book_multi，本机并不存在，导致 mvn test -Pit 直接启动失败
        "spring.datasource.url=jdbc:mysql://localhost:3306/library_test"
                + "?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
        "library.pay.alipay.mock-enabled=true"
})
class ReserveWakeUpTest {

    /** init.sql 里 stock=0 的《三体》，天然适合做售罄场景 */
    private static final long BOOK_ID = 6L;
    private static final long ZHANG = 2L;
    private static final long LI = 3L;
    private static final long WANG = 4L;

    @Autowired
    private ReserveService reserveService;
    @Autowired
    private BorrowService borrowService;
    @Autowired
    private BookItemService bookItemService;
    @Autowired
    private AgreementService agreementService;
    @Autowired
    private BookReserveMapper bookReserveMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        // 第三位读者：复制现有读者的密码 hash，避免在测试里出现明文密码
        jdbc.update("INSERT IGNORE INTO `user` "
                + "(`id`,`account`,`password`,`name`,`role`,`status`,`token_version`,`is_delete`,`create_time`,`update_time`) "
                + "SELECT 4, 'R20260003', `password`, '王五', 0, 0, 0, 0, NOW(), NOW() FROM `user` WHERE `id` = 2");
        for (long uid : new long[]{ZHANG, LI, WANG}) {
            agreementService.agree(uid, Constants.AGREEMENT_TYPE_BORROW, "127.0.0.1");
        }
        // 复位到「售罄且无人预约」的干净状态
        jdbc.update("DELETE FROM borrow WHERE book_id = ?", BOOK_ID);
        jdbc.update("DELETE FROM book_reserve WHERE book_id = ?", BOOK_ID);
        jdbc.update("DELETE FROM book_item WHERE book_id = ?", BOOK_ID);
        jdbc.update("DELETE FROM message");
        jdbc.update("UPDATE book SET stock = 0 WHERE id = ?", BOOK_ID);
        UserContext.clear();
    }

    @Test
    @DisplayName("一次补 2 册：唤醒 2 人，第 3 人仍排队，且未通知者借不走")
    void multiItemsWakeUpAllSlots() {
        reserveAs(ZHANG);
        reserveAs(LI);
        reserveAs(WANG);
        assertEquals(3, countByStatus(Constants.RESERVE_STATUS_QUEUING), "三人都应在排队");

        // 关键动作：一次补 2 册
        bookItemService.addItems(BOOK_ID, 2, "测试架位");

        assertEquals(2, countByStatus(Constants.RESERVE_STATUS_NOTIFIED),
                "一次补 2 册应唤醒 2 位，而不是只唤醒队首");
        assertEquals(1, countByStatus(Constants.RESERVE_STATUS_QUEUING),
                "第 3 位应继续排队");

        // 未被通知的人借不走，且提示要说清「你在排队」，不能让他以为没排上
        BusinessException blocked = assertThrows(BusinessException.class, () -> borrowAs(WANG));
        assertTrue(blocked.getMessage().contains("排队"),
                "被拦截时提示应说明在排队中，实际：" + blocked.getMessage());

        // 已被通知的人可以正常借
        assertDoesNotThrow(() -> borrowAs(ZHANG), "已收到通知的预约人应能借阅");
    }

    @Test
    @DisplayName("一次补 1 册：只唤醒 1 人（回归，不能超发名额）")
    void singleItemWakesUpOne() {
        reserveAs(ZHANG);
        reserveAs(LI);

        bookItemService.addItems(BOOK_ID, 1, "测试架位");

        assertEquals(1, countByStatus(Constants.RESERVE_STATUS_NOTIFIED), "补 1 册只应唤醒 1 人");
        assertEquals(1, countByStatus(Constants.RESERVE_STATUS_QUEUING), "另一位应继续排队");
    }

    @Test
    @DisplayName("补货唤醒预约时，应给被唤醒的读者推送「预约到书」站内信")
    void wakeUpSendsArrivalMessage() {
        reserveAs(ZHANG);
        reserveAs(LI);

        // 只补 1 册：只该唤醒队首，也只该给这一个人发通知
        bookItemService.addItems(BOOK_ID, 1, "测试架位");

        Long zhangMsg = jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE user_id = ? AND type = 3", Long.class, ZHANG);
        assertEquals(1L, zhangMsg, "被唤醒的读者应收到一条预约到书通知");

        Long liMsg = jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE user_id = ? AND type = 3", Long.class, LI);
        assertEquals(0L, liMsg, "还在排队的人不该收到到书通知，否则会白跑一趟");

        // 通知内容要能定位到具体书，否则用户不知道该来拿哪本
        String title = jdbc.queryForObject(
                "SELECT title FROM message WHERE user_id = ? AND type = 3", String.class, ZHANG);
        assertEquals("预约的图书已到馆", title);
        String content = jdbc.queryForObject(
                "SELECT content FROM message WHERE user_id = ? AND type = 3", String.class, ZHANG);
        assertTrue(content.contains("三体"), "通知正文应包含书名，实际：" + content);

        // 重复触发唤醒不能刷屏（该预约已不在排队状态）
        reserveService.wakeUpQueue(BOOK_ID);
        Long again = jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE user_id = ? AND type = 3", Long.class, ZHANG);
        assertEquals(1L, again, "同一预约单只应收到一条到书通知");
    }

    // ==================== 辅助 ====================

    private void reserveAs(long userId) {
        UserContext.set(userId, "u" + userId, Constants.ROLE_READER);
        reserveService.reserve(BOOK_ID);
        UserContext.clear();
    }

    private void borrowAs(long userId) {
        UserContext.set(userId, "u" + userId, Constants.ROLE_READER);
        try {
            borrowService.apply(BOOK_ID);
        } finally {
            UserContext.clear();
        }
    }

    private long countByStatus(int status) {
        Long n = bookReserveMapper.selectCount(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getBookId, BOOK_ID)
                .eq(BookReserve::getStatus, status));
        return n == null ? 0 : n;
    }
}
