package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.entity.PaymentFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资金流水 Mapper
 * <p>
 * 只提供插入与查询，不提供修改与删除——账本只能追加。
 */
@Mapper
public interface PaymentFlowMapper extends BaseMapper<PaymentFlow> {
}
