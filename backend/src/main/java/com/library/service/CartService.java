package com.library.service;

import com.library.vo.CartItemVO;
import com.library.vo.PayVO;

import java.util.List;

/**
 * 购物车服务：合并结算，一次支付完成多本借阅/购买
 */
public interface CartService {

    /**
     * 合并结算：逐本校验并锁副本 → 建 N 条业务单（借阅单/购买单）→ 建 1 个合并支付单
     *
     * @param items 结算条目（借阅 + 购买可混合）
     * @return 支付信息（含合并金额与收银台表单）
     */
    PayVO checkout(List<CartItemVO> items);
}
