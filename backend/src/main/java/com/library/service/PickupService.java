package com.library.service;

import com.library.vo.PickupPendingVO;
import com.library.vo.PickupResultVO;

import java.util.List;

/**
 * 取书服务
 * <p>
 * 一个用户一个取书码：到馆报这一个码，工作人员看到他的全部待取（借阅 + 购买），
 * 勾选后一次性核销。码 30 分钟有效，核销后作废，避免被他人冒领。
 */
public interface PickupService {

    /**
     * 读者生成（或刷新）取书码，覆盖旧码，返回 6 位数字
     */
    String generateCode();

    /**
     * 读者查看自己当前的取书码。
     * <p>
     * 只返回 userId/account/name/pickupCode/codeExpire 这几个字段；
     * 没有有效码（未生成或已过期）时 pickupCode 为空。
     */
    PickupPendingVO myCodeInfo();

    /**
     * 管理端：按取书码或读者账号查询待取清单
     *
     * @param clientIp 终端 IP，用于限流（防止逐个码枚举领取别人的书）
     */
    PickupPendingVO pending(String codeOrAccount, String clientIp);

    /**
     * 管理端：批量核销
     *
     * @param codeOrAccount 取书码或读者账号
     * @param borrowIds     要核销的借阅单 id
     * @param orderIds      要核销的购买订单 id
     * @param clientIp      终端 IP，用于限流
     */
    PickupResultVO pickup(String codeOrAccount, List<Long> borrowIds, List<Long> orderIds, String clientIp);
}
