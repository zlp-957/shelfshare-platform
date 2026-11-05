package com.library.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.common.PageUtil;
import com.library.entity.Borrow;
import com.library.entity.PayOrder;
import com.library.entity.ReconcileRecord;
import com.library.mapper.BorrowMapper;
import com.library.mapper.PayOrderMapper;
import com.library.mapper.ReconcileRecordMapper;
import com.library.vo.BillRow;
import com.library.vo.ReconcileResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对账服务。
 * <p>
 * 对账是资金系统的最后一道防线：前面所有的幂等、补偿、重试都是「尽量不出错」，
 * 只有把渠道账单和本地账本逐笔比对，才能回答「到底有没有错、错在哪一笔」。
 * <p>
 * 三类差异的含义：
 * <ul>
 *   <li><b>本地有、渠道无</b>：最危险。要么回调是伪造的（验签被绕过），
 *       要么查单补偿把一笔并不存在的交易记成了成功。处理方式是立即冻结对应业务单并人工核实。</li>
 *   <li><b>渠道有、本地无</b>：用户付了钱但系统不知道，典型掉单。
 *       补一条入账并推进业务单即可，用户无感知。</li>
 *   <li><b>金额不符</b>：可能被改价，也可能是部分退款后统计口径写错，需要人工判断。</li>
 * </ul>
 */
@Slf4j
@Service
public class ReconcileService {

    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private AlipayBillClient billClient;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private ReconcileRecordMapper reconcileRecordMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private BorrowMapper borrowMapper;

    /** 退款滞留判定阈值：超过这么多小时还停在「待重试」就报警 */
    @Value("${library.refund.stale-hours:24}")
    private int staleHours;

    /**
     * 对昨天的账（定时任务入口）
     */
    public ReconcileResultVO reconcileYesterday() {
        return reconcile(LocalDate.now().minusDays(1));
    }

    /**
     * 对指定自然日的账。
     * <p>
     * 可重复执行：同一账期重跑之前，会先清掉该批次「待处理」的差异重新生成，
     * 避免反复触发把差异表刷成一堆重复记录。已人工处理（已处理/已忽略）的保留。
     */
    /**
     * 对指定自然日的账。
     * <p>
     * 可重复执行：同一账期重跑之前，会先清掉该批次「待处理」的差异重新生成，
     * 避免反复触发把差异表刷成一堆重复记录。已人工处理（已处理/已忽略）的保留。
     * <p>
     * 注意这里刻意<b>不加 @Transactional</b>：拉账单是一次 HTTP 往返
     * （下载地址申请 + 下载 zip，最坏 20 秒），如果包在事务里，
     * 这段时间会一直占着数据库连接。和「退款不能待在核销事务里」是同一类问题。
     * 所以拆成两段：事务外拉数据，事务内只做比对与落库。
     */
    public ReconcileResultVO reconcile(LocalDate date) {
        if (date == null) {
            throw new BusinessException("请选择对账日期");
        }

        boolean simulated = false;
        List<BillRow> channelRows = billClient.fetch(date);
        if (channelRows == null) {
            // 拿不到真实账单（模拟支付 / 无权限 / 网络异常）：
            // 用本地数据构造渠道侧，把比对链路跑通，但结果必须标注为「模拟」
            channelRows = buildChannelSideFromLocal(date);
            simulated = true;
        }

        final List<BillRow> rows = channelRows;
        final boolean sim = simulated;
        return new TransactionTemplate(transactionManager)
                .execute(status -> compareAndPersist(date, rows, sim));
    }

    /**
     * 比对与落库（事务内执行）
     */
    private ReconcileResultVO compareAndPersist(LocalDate date, List<BillRow> channelRows, boolean simulated) {
        String batchNo = "REC" + date.format(BATCH_FMT);

        List<PayOrder> localOrders = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .ge(PayOrder::getPayTime, date.atStartOfDay())
                .lt(PayOrder::getPayTime, date.plusDays(1).atStartOfDay())
                .in(PayOrder::getStatus, Constants.PAY_STATUS_PAID,
                        Constants.PAY_STATUS_REFUNDED, Constants.PAY_STATUS_PARTIAL_REFUNDED));

        // 清掉本批次未处理的旧差异，保证重跑幂等
        reconcileRecordMapper.delete(new LambdaQueryWrapper<ReconcileRecord>()
                .eq(ReconcileRecord::getBatchNo, batchNo)
                .eq(ReconcileRecord::getStatus, ReconcileRecord.STATUS_PENDING));

        Map<String, BillRow> channelMap = new HashMap<>();
        for (BillRow row : channelRows) {
            if (row.getOutTradeNo() != null) {
                channelMap.put(row.getOutTradeNo(), row);
            }
        }
        Map<String, PayOrder> localMap = new HashMap<>();
        for (PayOrder order : localOrders) {
            localMap.put(order.getOrderNo(), order);
        }

        List<ReconcileRecord> diffs = new ArrayList<>();

        // 本地有：检查渠道侧是否存在、金额是否一致
        for (PayOrder order : localOrders) {
            BillRow channel = channelMap.get(order.getOrderNo());
            if (channel == null) {
                diffs.add(buildDiff(batchNo, date, order.getOrderNo(), null, order.getTradeNo(),
                        order.getAmount(), null, ReconcileRecord.DIFF_LOCAL_ONLY,
                        simulated
                                ? "模拟对账：本地有此单，构造的渠道数据中未匹配到"
                                : "本地记为已支付，渠道账单中无此交易，需核实是否伪造回调"));
                continue;
            }
            if (channel.getAmount() != null
                    && channel.getAmount().compareTo(order.getAmount()) != 0) {
                diffs.add(buildDiff(batchNo, date, order.getOrderNo(), null, order.getTradeNo(),
                        order.getAmount(), channel.getAmount(), ReconcileRecord.DIFF_AMOUNT,
                        "本地金额与渠道金额不一致"));
            }
        }

        // 渠道有：检查本地是否漏记
        for (BillRow row : channelRows) {
            if (row.getOutTradeNo() == null || localMap.containsKey(row.getOutTradeNo())) {
                continue;
            }
            diffs.add(buildDiff(batchNo, date, row.getOutTradeNo(), null, row.getTradeNo(),
                    null, row.getAmount(), ReconcileRecord.DIFF_CHANNEL_ONLY,
                    "渠道已收款但本地无对应已支付单，疑似回调丢失"));
        }

        // 第三类差异不来自账单比对，而是本地状态扫描：退款滞留
        appendStaleRefundDiffs(batchNo, date, diffs);

        // 关键判断：账单整体为空、本地却有已支付单时，这轮其实「没得对」。
        // 此时如果把 4 笔全报成「本地有渠道无」，管理员会看到满屏「疑似伪造回调」，
        // 把正常交易当成事故。既然无法判定，就不写差异表，只在结果里说清楚。
        boolean billEmptyWhileLocalExists = channelRows.isEmpty() && !localOrders.isEmpty();

        // 退款滞留属于本地状态异常，和账单是否为空无关，无论哪种情况都要保留
        List<ReconcileRecord> tradeDiffs = new ArrayList<>();
        List<ReconcileRecord> staleDiffs = new ArrayList<>();
        for (ReconcileRecord diff : diffs) {
            if (diff.getDiffType() == ReconcileRecord.DIFF_REFUND_STALE) {
                staleDiffs.add(diff);
            } else {
                tradeDiffs.add(diff);
            }
        }
        List<ReconcileRecord> toSave = new ArrayList<>(staleDiffs);
        if (!billEmptyWhileLocalExists) {
            toSave.addAll(tradeDiffs);
        } else if (!tradeDiffs.isEmpty()) {
            log.warn("渠道账单为空，本次跳过 {} 笔交易差异不写入 date={} 本地 {} 笔",
                    tradeDiffs.size(), date, localOrders.size());
        }

        for (ReconcileRecord diff : toSave) {
            reconcileRecordMapper.insert(diff);
        }
        if (!toSave.isEmpty()) {
            log.warn("对账发现 {} 笔差异 date={} batchNo={}", toSave.size(), date, batchNo);
        } else {
            log.info("对账完成，无差异 date={} 本地 {} 笔", date, localOrders.size());
        }

        ReconcileResultVO vo = new ReconcileResultVO();
        vo.setBillDate(date);
        vo.setSimulated(simulated);
        vo.setLocalCount(localOrders.size());
        vo.setChannelCount(channelRows.size());
        vo.setDiffCount(toSave.size());

        // 这里要区分「真差异」和「环境导致的假差异」。
        // 规则说明：渠道账单为空、本地却有已支付单时，几乎不可能是业务问题——
        // 沙箱环境不把沙箱交易写进正式账单，账期当天也可能还没生成。
        String message;
        if (simulated) {
            message = "当前为模拟对账（渠道账单不可用），渠道侧数据由本地已支付单构造，仅用于验证比对链路";
        } else if (billEmptyWhileLocalExists) {
            message = "渠道账单为空，但本地有 " + localOrders.size()
                    + " 笔已支付单。本次未做差异判定，也没有写入差异表——"
                    + "常见原因是账期还没生成、或当前环境（沙箱）不提供账单数据，请先核实账单本身";
        } else if (toSave.isEmpty()) {
            message = "账实一致，未发现差异";
        } else {
            message = "发现 " + toSave.size() + " 笔差异，需人工核实";
        }
        vo.setMessage(message);
        return vo;
    }

    /**
     * 模拟渠道侧数据：直接取本地已支付单，因此比对结果必然一致。
     * <p>
     * 必须和「本地侧」用同一个状态口径（1 已支付 / 3 已全额退款 / 4 部分退款）。
     * 之前这里只取了 status=1，而本地侧包含退款单，结果每一笔已退款订单
     * 都会被判成「本地有、渠道无」——模拟模式下 100% 产生假差异，
     * 管理端会把这些正常退款单误当成疑似伪造回调。
     * <p>
     * 这不是「造假通过」——它的用途是验证解析、比对、落库、展示这条链路是否通，
     * 真实效果必须在关闭 mock 后接沙箱账单才有意义。
     */
    private List<BillRow> buildChannelSideFromLocal(LocalDate date) {
        List<PayOrder> local = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .ge(PayOrder::getPayTime, date.atStartOfDay())
                .lt(PayOrder::getPayTime, date.plusDays(1).atStartOfDay())
                // 与 compareAndPersist 的本地侧保持完全一致：
                // 渠道账单是按「收款」记的，退款与否不影响这笔交易在账单里的存在，
                // 所以已退款单同样必须出现在渠道侧
                .in(PayOrder::getStatus, Constants.PAY_STATUS_PAID,
                        Constants.PAY_STATUS_REFUNDED, Constants.PAY_STATUS_PARTIAL_REFUNDED));
        List<BillRow> rows = new ArrayList<>();
        for (PayOrder order : local) {
            BillRow row = new BillRow();
            row.setOutTradeNo(order.getOrderNo());
            row.setTradeNo(order.getTradeNo());
            row.setAmount(order.getAmount());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 扫描「退款滞留」：标记待重试但长时间没退成功的押金。
     * <p>
     * 为什么需要这一类：交易对账只能发现「渠道和本地对不上」，
     * 发现不了「本地自己没走完」。押金退款失败后由定时任务补偿，
     * 如果补偿连续失败（渠道一直报错、配置错、证书过期），
     * 状态会永远停在 {@code refund_status=2} 而没有任何人知道——
     * 用户的钱就这么悬着，直到他打电话来问。
     * <p>
     * 计时起点用 {@code refund_fail_time} 而不是 {@code update_time}：
     * 后者会被任何无关更新（数据修正、其他字段回写）推后，导致该报的滞留被延迟。
     * 老数据没有这个字段，用 update_time 兜底，保证升级前的滞留也能被扫到。
     * <p>
     * 去重按 {@code order_no + borrow_id}：合并支付单下一笔钱可能对应多笔借阅，
     * 只按订单号去重会漏报第二笔。
     */
    private void appendStaleRefundDiffs(String batchNo, LocalDate date, List<ReconcileRecord> diffs) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(staleHours);
        List<Borrow> staleList = borrowMapper.selectList(new LambdaQueryWrapper<Borrow>()
                // 只有已归还/已取消的单才该有退款；借阅中的单没有退款义务
                .in(Borrow::getStatus,
                        Constants.BORROW_STATUS_RETURNED, Constants.BORROW_STATUS_CANCELED)
                .eq(Borrow::getRefundStatus, Constants.REFUND_STATUS_FAILED)
                .and(w -> w.lt(Borrow::getRefundFailTime, threshold)
                        .or(x -> x.isNull(Borrow::getRefundFailTime)
                                .lt(Borrow::getUpdateTime, threshold))));

        for (Borrow borrow : staleList) {
            if (borrow.getPayOrderNo() == null) {
                continue;
            }
            Long exist = reconcileRecordMapper.selectCount(new LambdaQueryWrapper<ReconcileRecord>()
                    .eq(ReconcileRecord::getDiffType, ReconcileRecord.DIFF_REFUND_STALE)
                    .eq(ReconcileRecord::getOrderNo, borrow.getPayOrderNo())
                    .eq(ReconcileRecord::getBorrowId, borrow.getId())
                    .in(ReconcileRecord::getStatus,
                            ReconcileRecord.STATUS_PENDING, ReconcileRecord.STATUS_IGNORED));
            if (exist != null && exist > 0) {
                continue;
            }
            BigDecimal refund = borrow.getRefundAmount() == null
                    ? BigDecimal.ZERO : borrow.getRefundAmount();
            LocalDateTime since = borrow.getRefundFailTime() != null
                    ? borrow.getRefundFailTime() : borrow.getUpdateTime();
            long waitedHours = since == null ? staleHours
                    : java.time.Duration.between(since, LocalDateTime.now()).toHours();
            diffs.add(buildDiff(batchNo, date, borrow.getPayOrderNo(), borrow.getId(), null,
                    refund, null, ReconcileRecord.DIFF_REFUND_STALE,
                    "押金退款已等待 " + waitedHours + " 小时仍未成功（借阅单 " + borrow.getId()
                            + "），补偿任务可能持续失败，需人工核实"));
        }
    }

    private ReconcileRecord buildDiff(String batchNo, LocalDate date, String orderNo, Long borrowId,
                                      String tradeNo,
                                      BigDecimal localAmount, BigDecimal channelAmount,
                                      int diffType, String remark) {
        ReconcileRecord record = new ReconcileRecord();
        record.setBatchNo(batchNo);
        record.setBillDate(date);
        record.setOrderNo(orderNo);
        record.setBorrowId(borrowId);
        record.setTradeNo(tradeNo);
        record.setLocalAmount(localAmount);
        record.setChannelAmount(channelAmount);
        record.setDiffAmount(localAmount == null ? null : localAmount.subtract(
                channelAmount == null ? BigDecimal.ZERO : channelAmount));
        record.setDiffType(diffType);
        record.setStatus(ReconcileRecord.STATUS_PENDING);
        record.setRemark(remark);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    /**
     * 管理端分页查询差异
     */
    public Page<ReconcileRecord> page(Integer page, Integer size, Integer status, String keyword) {
        LambdaQueryWrapper<ReconcileRecord> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ReconcileRecord::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ReconcileRecord::getOrderNo, kw)
                    .or().like(ReconcileRecord::getBatchNo, kw));
        }
        wrapper.orderByDesc(ReconcileRecord::getId);
        return reconcileRecordMapper.selectPage(PageUtil.of(page, size), wrapper);
    }

    /**
     * 处理一条差异：resolved=true 标记已处理，false 标记忽略
     */
    public void handle(Long id, boolean resolved, String remark) {
        ReconcileRecord record = reconcileRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("差异记录不存在");
        }
        record.setStatus(resolved ? ReconcileRecord.STATUS_RESOLVED : ReconcileRecord.STATUS_IGNORED);
        record.setRemark(StringUtils.hasText(remark) ? remark : record.getRemark());
        record.setUpdateTime(LocalDateTime.now());
        reconcileRecordMapper.updateById(record);
    }
}
