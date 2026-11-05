package com.library.service;

import com.library.entity.PayOrder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付服务
 * <p>
 * 与具体渠道解耦的支付能力：创建支付单、生成收银台表单、处理回调、
 * 主动查单、退款、关单。
 */
public interface PayService {

    /**
     * 创建支付单（落库，状态待支付）
     *
     * @param userId  付款用户
     * @param bizType 业务类型：1借阅押金 2购买书款
     * @param bizId   关联业务id
     * @param amount  金额（以服务端计算为准，不接受前端传值）
     */
    PayOrder createOrder(Long userId, int bizType, Long bizId, BigDecimal amount);

    /**
     * 生成支付宝收银台跳转表单（HTML 字符串）
     */
    String buildPayForm(String orderNo);

    /**
     * 处理支付宝异步回调
     * <p>
     * 验签 → 状态判断 → 金额比对 → 幂等闸门 → 写流水 → 发事件
     *
     * @param params 支付宝 POST 过来的全部参数
     */
    void handleNotify(Map<String, String> params);

    /**
     * 主动查单并同步状态，用于回调丢失时的兜底
     *
     * @return 本次是否发生了状态推进（由未支付变为已支付）
     */
    boolean syncStatus(String orderNo);

    /**
     * 退款
     *
     * @param orderNo   支付单号
     * @param amount    退款金额
     * @param reason    退款原因
     * @param requestNo 退款请求号（幂等键，同一值重复调用只退一次）
     */
    void refund(String orderNo, BigDecimal amount, String reason, String requestNo);

    /**
     * 关闭订单
     */
    void closeOrder(String orderNo);

    /**
     * 按单号查询
     */
    PayOrder getByOrderNo(String orderNo);

    /**
     * 模拟支付成功（仅 mock 模式可用）
     * <p>
     * 走与真实回调完全相同的入账逻辑，用于本地联调与自动化测试。
     */
    void mockPay(String orderNo);

    /**
     * 当前是否处于模拟支付模式
     */
    boolean isMockMode();
}
