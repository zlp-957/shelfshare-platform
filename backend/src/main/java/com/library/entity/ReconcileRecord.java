package com.library.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账差异记录。
 * <p>
 * 每天拉一次渠道账单与本地支付单逐笔核对，只把「对不上」的写进来。
 * 全部一致的日子表里就是空的，这本身就是结论。
 */
@Data
@TableName("reconcile_record")
public class ReconcileRecord {

    /** 差异类型：本地有渠道无（疑似伪造回调 / 掉单） */
    public static final int DIFF_LOCAL_ONLY = 1;
    /** 差异类型：渠道有本地无（回调丢失且补偿未覆盖） */
    public static final int DIFF_CHANNEL_ONLY = 2;
    /** 差异类型：金额不符 */
    public static final int DIFF_AMOUNT = 3;
    /**
     * 差异类型：退款滞留（标记待退但长时间未退成功）。
     * <p>
     * 前三种是「渠道与本地对不上」，这一种是「本地自己没走完」。
     * 它不来自账单比对，而是来自状态扫描：{@code refund_status=2} 且超过阈值仍在等的记录。
     * 为什么需要它：押金退款失败后靠定时任务补偿，如果补偿连续失败，
     * 状态会一直停在「待重试」而没人发现——用户的钱就这么悬着。
     * 把这件事放到对账页上，它才会被看见。
     */
    public static final int DIFF_REFUND_STALE = 4;

    /** 处理状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 处理状态：已处理 */
    public static final int STATUS_RESOLVED = 1;
    /** 处理状态：已忽略（差异可解释，人工确认后处理） */
    public static final int STATUS_IGNORED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对账批次号：REC + yyyyMMdd */
    private String batchNo;

    /** 账期（自然日） */
    private LocalDate billDate;

    /** 本地支付单号 */
    private String orderNo;

    /** 渠道交易号 */
    private String tradeNo;

    /**
     * 关联借阅单 id（退款滞留类差异用）。
     * <p>
     * 一个合并支付单可能对应多笔借阅，各自有独立的押金和退款。
     * 只按 order_no 去重会导致「两笔都滞留、只报一条」，另一笔被掩盖。
     * 按 borrow_id 去重才能一笔一条。
     */
    private Long borrowId;

    /** 本地金额 */
    private BigDecimal localAmount;

    /** 渠道金额 */
    private BigDecimal channelAmount;

    /** 差额：本地 - 渠道 */
    private BigDecimal diffAmount;

    /** 差异类型，见本类常量 */
    private Integer diffType;

    /** 处理状态 */
    private Integer status;

    /** 处理备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
