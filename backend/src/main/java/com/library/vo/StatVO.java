package com.library.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 统计数据视图对象
 */
@Data
public class StatVO {

    /** 书目总数 */
    private Long bookCount;

    /** 副本总数（实体书册数） */
    private Long itemCount;

    /** 在馆可借副本数 */
    private Long availableItemCount;

    /** 读者总数 */
    private Long readerCount;

    /** 借阅中数量 */
    private Long borrowingCount;

    /** 待取书数量（已付押金，等待到馆核销） */
    private Long pendingPickupCount;

    /** 已买断数量（逾期未还，押金已没收） */
    private Long boughtOutCount;

    /** 预约排队中数量 */
    private Long queuingReserveCount;

    /** 已支付购买订单数 */
    private Long purchaseCount;

    /** 购买总金额 */
    private BigDecimal totalPurchaseAmount;

    /** 在借押金总额（占用中的担保金额） */
    private BigDecimal depositInUse;
}
