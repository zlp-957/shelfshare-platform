package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.entity.PayOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    /**
     * 幂等置为已支付。
     * <p>
     * 条件 status = 0 保证并发回调下只有一次能成功。
     * 返回 0 表示已被处理过（重复回调 / 查单补偿撞车），调用方应直接返回成功。
     */
    @Update("UPDATE pay_order SET status = 1, trade_no = #{tradeNo}, "
            + "pay_time = #{payTime}, update_time = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 0 AND is_delete = 0")
    int markPaid(@Param("orderNo") String orderNo,
                 @Param("tradeNo") String tradeNo,
                 @Param("payTime") LocalDateTime payTime);

    /**
     * 关闭超时未支付订单。
     */
    @Update("UPDATE pay_order SET status = 2, update_time = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 0 AND is_delete = 0")
    int closeOrder(@Param("orderNo") String orderNo);

    /**
     * 累加退款金额并推进状态。
     * <p>
     * 两个约束在 SQL 里完成，不依赖应用层判断：
     * - status IN (1,4)：只有已支付或部分退款的单能退
     * - refund_amount + amount <= amount：累计退款不能超过原交易金额
     * 影响行数为 0 说明超退或状态不符，必须抛错回滚。
     * <p>
     * 注意 SET 的求值顺序：MySQL 按从左到右求值，后面的表达式会读到前面刚更新的列。
     * 所以 status 必须写在 refund_amount 之前——否则它读到的是「累加后」的金额，
     * 把本次金额又算了一遍，导致部分退款（退 358 / 总额 403）也被判成 3=已退款。
     */
    @Update("UPDATE pay_order SET "
            + "status = IF(refund_amount + #{amount} >= amount, 3, 4), "
            + "refund_amount = refund_amount + #{amount}, "
            + "update_time = NOW() "
            + "WHERE order_no = #{orderNo} AND status IN (1, 4) "
            + "AND refund_amount + #{amount} <= amount AND is_delete = 0")
    int addRefund(@Param("orderNo") String orderNo, @Param("amount") BigDecimal amount);
}
