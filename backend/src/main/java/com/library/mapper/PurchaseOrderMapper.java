package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {

    /**
     * 已支付购买单数量。
     * <p>
     * 用 SQL 聚合而不是把全部订单查进内存再 count，数据量上来会撑爆堆。
     */
    @Select("SELECT COUNT(*) FROM purchase_order WHERE status = 1 AND is_delete = 0")
    Long countPaid();

    /**
     * 已支付购买总额
     */
    @Select("SELECT COALESCE(SUM(price), 0) FROM purchase_order WHERE status = 1 AND is_delete = 0")
    BigDecimal sumPaidAmount();
}
