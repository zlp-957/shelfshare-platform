package com.library.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.common.Constants;
import com.library.entity.PaymentFlow;
import com.library.mapper.PaymentFlowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金流水写入
 * <p>
 * 账本只能追加，本类不提供任何修改或删除方法。
 * 所有涉及资金变动的业务（收押金、退押金、扣款、买断）都必须经过这里落一条流水，
 * 否则对账时会出现「钱动了但账上没有」的情况。
 */
@Service
public class PaymentFlowService {

    @Autowired
    private PaymentFlowMapper paymentFlowMapper;

    /**
     * 记一笔流水
     *
     * @param userId    用户id
     * @param bizType   流水类型（Constants.FLOW_TYPE_*）
     * @param bizId     关联业务id
     * @param bizNo     关联单号
     * @param direction 方向：1收入 2支出
     * @param amount    金额（正数）
     * @param remark    备注
     */
    public void record(Long userId, int bizType, Long bizId, String bizNo,
                       int direction, BigDecimal amount, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        PaymentFlow flow = new PaymentFlow();
        flow.setUserId(userId);
        flow.setBizType(bizType);
        flow.setBizId(bizId);
        flow.setBizNo(bizNo);
        flow.setDirection(direction);
        flow.setAmount(amount);
        flow.setRemark(remark);
        flow.setCreateTime(LocalDateTime.now());
        paymentFlowMapper.insert(flow);
    }

    /**
     * 该业务单是否已经产生过押金退款流水。
     * <p>
     * 这是退款幂等的最后一道闸门。正常情况下靠支付宝按 out_request_no 幂等就够了，
     * 但本地记的「累计退款金额」是另一回事：渠道不重复退，本地 addRefund 却会再加一次，
     * 结果是账本虚增。用流水表判断「这件事是否已经发生过」，比状态位更可靠——
     * 状态位可能因为中途失败没更新，而流水只在真正退成功后才写。
     */
    public boolean hasDepositRefund(Long bizId) {
        if (bizId == null) {
            return false;
        }
        Long count = paymentFlowMapper.selectCount(new LambdaQueryWrapper<PaymentFlow>()
                .eq(PaymentFlow::getBizType, Constants.FLOW_TYPE_DEPOSIT_REFUND)
                .eq(PaymentFlow::getBizId, bizId));
        return count != null && count > 0;
    }
}
