package com.library.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.vo.BorrowSettleVO;
import com.library.vo.BorrowVO;
import com.library.vo.PayVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 借阅服务（押金制 + 到馆核销）
 */
public interface BorrowService {

    /**
     * 申请借阅：锁定一册在馆副本 → 创建借阅单（待取书）→ 创建押金支付单
     *
     * @return 支付信息（含收银台表单）
     */
    PayVO apply(Long bookId);

    /**
     * 借出核销：管理员扫副本条码 + 读者账号
     * <p>
     * 校验 5 项后，副本由「已预留」转「借出中」，借期从此刻起算。
     */
    BorrowSettleVO checkOut(String barcode, String readerAccount);

    /**
     * 借出核销（按借阅单 id）：批量取书核销的原子操作。
     * <p>
     * 一条借阅单对应一册已预留的副本，核销后副本转「借出中」，借期从此刻起算。
     */
    BorrowSettleVO checkOutById(Long borrowId);

    /**
     * 归还核销：管理员扫副本条码
     * <p>
     * 计算逾期费与破损赔偿，从押金扣除后原路退回剩余金额。
     */
    BorrowSettleVO checkIn(String barcode, BigDecimal damageFee);

    /**
     * 用户取消待取书的借阅（全额退押金，释放副本）
     */
    void cancel(Long borrowId);

    /**
     * 我的借阅记录
     */
    List<BorrowVO> myBorrows(Integer status);

    /**
     * 按用户查借阅记录（管理端取书核销用，可查任意读者）
     */
    List<BorrowVO> listByUser(Long userId, Integer status);

    /**
     * 管理员分页查询借阅记录
     */
    Page<BorrowVO> pageBorrows(Integer page, Integer size, String keyword, Integer status);

    /**
     * 逾期定时任务：逾期满约定期限或押金扣光的，转为买断
     */
    void processOverdue();

    /**
     * 超时未取书定时任务：支付后超过约定小时数未核销，退款并取消
     */
    void cancelExpiredPending();

    /**
     * 押金支付单超时/关闭时级联取消借阅单，并把锁定的副本释放回在馆
     * <p>
     * 支付单 15 分钟就过期，而待取书要 2 小时才清理。中间这段时间副本一直被锁着，
     * 别人借不到，所以关单时必须同步释放。
     *
     * @param payOrderNo 支付单号
     */
    void cancelByPayOrder(String payOrderNo);

    /**
     * 退款补偿定时任务：重试归还时失败的押金退款
     * <p>
     * 归还流程不因退款失败而回滚，失败的记录标记为待重试，由本任务按幂等请求号补退。
     */
    void retryFailedRefunds();
}
