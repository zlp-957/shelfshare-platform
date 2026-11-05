package com.library.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.vo.PayVO;
import com.library.vo.PurchaseVO;

import java.util.List;

/**
 * 购买服务
 */
public interface PurchaseService {

    /**
     * 购买图书：锁定一册在馆副本 → 创建购买单（待支付）→ 创建书款支付单
     *
     * @return 支付信息（含收银台表单）
     */
    PayVO buy(Long bookId);

    /**
     * 我的订单
     */
    List<PurchaseVO> myOrders();

    /**
     * 按用户查订单（管理端取书核销用）
     */
    List<PurchaseVO> listByUser(Long userId);

    /**
     * 用户取消「待取书」订单：全额退书款，副本释放回馆藏。
     * <p>
     * 只有在付了款但还没到馆取书时才允许；已取书的交易视为完成，不支持退款。
     */
    void cancelPending(Long orderId);

    /**
     * 管理员核销取书：扫副本条码，把「待取书」订单推进为「已取书」，副本转已售出。
     */
    PurchaseVO pickup(String barcode);

    /**
     * 管理员核销取书（按订单 id）：批量取书核销的原子操作。
     */
    PurchaseVO pickupByOrderId(Long orderId);

    /**
     * 管理员分页查询订单
     */
    Page<PurchaseVO> pageOrders(Integer page, Integer size, String keyword, Integer buyType);

    /**
     * 支付单超时/关闭时级联取消购买单，并把锁定的副本释放回在馆
     * <p>
     * 由定时关单任务调用。没有这一步，超时未支付的购买单会永久占着一册库存。
     *
     * @param payOrderNo 支付单号
     */
    void cancelByPayOrder(String payOrderNo);
}
