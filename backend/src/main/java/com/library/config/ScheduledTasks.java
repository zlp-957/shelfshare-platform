package com.library.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.common.Constants;
import com.library.entity.PayOrder;
import com.library.mapper.PayOrderMapper;
import com.library.service.BorrowService;
import com.library.service.MessageService;
import com.library.service.PasswordResetService;
import com.library.service.PayService;
import com.library.service.PurchaseService;
import com.library.service.ReconcileService;
import com.library.service.ReserveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务
 * <p>
 * 逾期买断、超时未取书、预约失效、支付查单补偿、超时关单。
 */
@Slf4j
@Component
public class ScheduledTasks {

    @Autowired
    private BorrowService borrowService;

    @Autowired
    private ReserveService reserveService;

    @Autowired
    private PayService payService;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ReconcileService reconcileService;

    /** 到期前多少天开始提醒 */
    @Value("${library.message.due-ahead-days:3}")
    private int dueAheadDays;

    /**
     * 每小时扫描一次借阅到期与逾期，生成站内信提醒。
     * <p>
     * 幂等由 message.dedupe_key 的唯一索引兜底，所以每小时跑不会重复打扰用户。
     */
    @Scheduled(cron = "0 5 * * * ?")
    public void remindBorrowDue() {
        try {
            int n = messageService.scanBorrowReminders(dueAheadDays);
            if (n > 0) {
                log.info("生成借阅提醒 {} 条", n);
            }
        } catch (Exception e) {
            log.error("借阅到期提醒任务失败", e);
        }
    }

    /**
     * 每天凌晨 2 点跑一次对账。
     * <p>
     * 对的是「昨天」的账：支付宝账单按自然日生成，当天跑拿不到完整数据。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyReconcile() {
        try {
            reconcileService.reconcileYesterday();
        } catch (Exception e) {
            log.error("对账任务执行失败", e);
        }
    }

    /**
     * 每 10 分钟清理过期的密码重置申请。
     * <p>
     * 不清的话，过期申请会一直占着「已发码」状态，读者重新申请被挡、
     * 管理员发码和拒绝也都被状态拦住，形成只能改库才能恢复的死锁。
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void expireStaleResetRequests() {
        try {
            int n = passwordResetService.expireStale();
            if (n > 0) {
                log.info("已作废 {} 条过期密码重置申请", n);
            }
        } catch (Exception e) {
            log.error("清理过期密码重置申请失败", e);
        }
    }

    /**
     * 每小时扫描逾期未还：逾期满约定期限的，转为买断
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void processOverdue() {
        try {
            borrowService.processOverdue();
        } catch (Exception e) {
            log.error("逾期买断定时任务执行失败", e);
        }
    }

    /**
     * 每 10 分钟处理超时未取书：支付后超时仍未到馆核销的，退款并取消
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void cancelExpiredPending() {
        try {
            borrowService.cancelExpiredPending();
        } catch (Exception e) {
            log.error("超时未取书处理失败", e);
        }
    }

    /**
     * 每 10 分钟补偿一次失败的押金退款
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void retryFailedRefunds() {
        try {
            borrowService.retryFailedRefunds();
        } catch (Exception e) {
            log.error("退款补偿任务失败", e);
        }
    }

    /**
     * 每小时扫描一次失效预约（顺延下一位）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void processExpiredReserve() {
        try {
            reserveService.processExpired();
        } catch (Exception e) {
            log.error("预约失效处理失败", e);
        }
    }

    /**
     * 每 5 分钟查单补偿：回调可能因网络、域名变更而丢失，没有这一步掉单就是资损
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void syncUnpaidOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        List<PayOrder> list = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getStatus, Constants.PAY_STATUS_UNPAID)
                .lt(PayOrder::getCreateTime, threshold)
                .gt(PayOrder::getExpireTime, LocalDateTime.now()));
        for (PayOrder order : list) {
            try {
                payService.syncStatus(order.getOrderNo());
            } catch (Exception e) {
                log.error("查单补偿失败 orderNo={}", order.getOrderNo(), e);
            }
        }
    }

    /**
     * 每分钟关闭超时未支付订单
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void closeExpiredOrders() {
        List<PayOrder> list = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getStatus, Constants.PAY_STATUS_UNPAID)
                .lt(PayOrder::getExpireTime, LocalDateTime.now()));
        for (PayOrder order : list) {
            try {
                payService.closeOrder(order.getOrderNo());
                // 关单后必须级联取消业务单并释放副本。
                // 只关支付单的话，购买单会一直占着一册「已预留」的库存，别人看不到也借不到。
                if (order.getBizType() == Constants.PAY_BIZ_PURCHASE) {
                    purchaseService.cancelByPayOrder(order.getOrderNo());
                } else if (order.getBizType() == Constants.PAY_BIZ_DEPOSIT) {
                    borrowService.cancelByPayOrder(order.getOrderNo());
                } else if (order.getBizType() == Constants.PAY_BIZ_COMBINED) {
                    // 合并订单可能同时含借阅与购买，两边都要释放
                    borrowService.cancelByPayOrder(order.getOrderNo());
                    purchaseService.cancelByPayOrder(order.getOrderNo());
                }
            } catch (Exception e) {
                log.error("关闭超时支付单失败 orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
