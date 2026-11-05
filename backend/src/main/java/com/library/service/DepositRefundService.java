package com.library.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.library.common.Constants;
import com.library.entity.Borrow;
import com.library.mapper.BorrowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 押金退款执行器（独立事务）。
 * <p>
 * 为什么要单独拆出来：<b>渠道退款不能待在核销事务里</b>。
 * <p>
 * 归还核销原本是「改单据 + 改副本 + 调支付宝退款」全在一个事务内完成。
 * 支付宝退款是一次 HTTP 往返（沙箱实测 0.7~1.2 秒），这段时间事务一直持有
 * borrow / book_item 的行锁不放。管理员连续扫码还书时请求会串行排队，
 * 连接池被慢事务占满，前端表现为「扫码没反应」。
 * <p>
 * 现在的顺序是：
 * <ol>
 *   <li>核销事务内先把「应退金额 + 待退状态」落库（意图先记录）；</li>
 *   <li>事务提交后（afterCommit）再调用渠道退款；</li>
 *   <li>退款失败保持「待重试」，由定时任务用同一个幂等请求号补偿。</li>
 * </ol>
 * 这样即使退款时进程崩溃，钱和单据也不会对不上：补偿任务能扫到待退记录，
 * 而支付宝按 out_request_no 幂等，重复调用不会多退一次。
 */
@Slf4j
@Service
public class DepositRefundService {

    @Autowired
    private PayService payService;

    @Autowired
    private BorrowMapper borrowMapper;

    @Autowired
    private PaymentFlowService paymentFlowService;

    /**
     * 执行退款并回写结果。必须开新事务——它是在原事务提交之后被调用的，
     * 此时旧事务已经结束，用 REQUIRED 会没有事务可用（每次 SQL 自动提交，
     * 退款成功但状态回写失败就再也补不上了）。
     * <p>
     * <b>方法内刻意不 catch 异常。</b> 曾经这里把异常吞掉并打一条日志，
     * 表面看「退款失败也不影响主流程」，实际留下一个金额虚增的窗口：
     * 渠道已退 → markRefund 失败（并发行锁冲突）→ 异常被吞、事务照常提交，
     * 结果是本地累计退款没加、流水也没写、状态还是失败；
     * 补偿任务用「没有流水」判断会认为从未退过，于是再退一次，账面金额翻倍。
     * <p>
     * 正确做法是让异常抛出去，整个 REQUIRES_NEW 事务回滚干净，
     * 本地状态停在调用前写好的「待重试」。重试是安全的：
     * 退款请求号固定 {@code borrowId_R1}，支付宝按 out_request_no 幂等，不会多退一次。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void refund(Long borrowId, Long userId, String payOrderNo,
                       BigDecimal amount, String reason) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        // 幂等第一道：已经退过就不要再退。
        // 真实环境靠支付宝的 out_request_no 幂等，但本地「累计退款金额」是独立记账，
        // 渠道不重复退、本地却会再加一次，账目就虚高了。用流水表做最终判据。
        if (paymentFlowService.hasDepositRefund(borrowId)) {
            markRefund(borrowId, Constants.REFUND_STATUS_SUCCESS);
            log.info("该借阅已有押金退款流水，跳过重复退款 borrowId={}", borrowId);
            return;
        }

        // 三步必须同生共死：渠道退款 → 本地加款 → 写流水。
        // 任何一步失败都整体回滚，状态停在「待重试」，由补偿任务重新走一遍
        payService.refund(payOrderNo, amount, reason, borrowId + "_R1");
        markRefund(borrowId, Constants.REFUND_STATUS_SUCCESS);
        paymentFlowService.record(userId, Constants.FLOW_TYPE_DEPOSIT_REFUND,
                borrowId, payOrderNo, Constants.FLOW_DIRECTION_OUT, amount, reason);
        log.info("押金退款成功 borrowId={} 金额={}", borrowId, amount);
    }

    private void markRefund(Long borrowId, int status) {
        borrowMapper.update(null, new LambdaUpdateWrapper<Borrow>()
                .eq(Borrow::getId, borrowId)
                .set(Borrow::getRefundStatus, status));
    }
}
