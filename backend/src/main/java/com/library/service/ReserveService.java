package com.library.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.vo.ReserveVO;

import java.util.List;

/**
 * 图书预约服务
 */
public interface ReserveService {

    /**
     * 提交预约（库存=0时）
     */
    void reserve(Long bookId);

    /**
     * 取消预约
     */
    void cancel(Long reserveId);

    /**
     * 我的预约列表
     */
    List<ReserveVO> myReserves();

    /**
     * 管理员分页查询预约
     */
    Page<ReserveVO> pageReserves(Integer page, Integer size, String keyword, Integer status);

    /**
     * 处理失效预约（超过有效期未借阅，顺延下一位）
     */
    void processExpired();

    /**
     * 校验当前用户是否有资格借走这本书（预约优先权）。
     * <p>
     * 规则：每条「待借阅」预约各占一册，只有超出这部分的名额才对无预约读者开放；
     * 自己若正是被通知的预约人，天然有资格。
     * <p>
     * 抽成公共方法是因为借阅有两条入口——单人下单 /borrow/apply 与 购物车合并结算
     * /cart/checkout。只在一处校验，另一处就能绕过预约优先权把书抢走。
     *
     * @param userId 借阅人
     * @param bookId 书目 id
     * @param stock  当前在馆库存（调用方以自己查询到的库存为准）
     * @throws com.library.common.BusinessException 无资格时抛出，消息已按「是否已在队列」区分
     */
    void assertBorrowAllowed(Long userId, Long bookId, int stock);

    /**
     * 库存增加后唤醒排队队列第一位。
     * <p>
     * 唤醒时机不止「归还」一处：管理员补货（新增副本）、把破损/下架副本改回在馆，
     * 都会凭空多出可借库存。这些路径若不唤醒，队列会一直停着，
     * 读者只能等预约过期或有人取消才被通知。
     *
     * @param bookId 书目 id
     */
    void wakeUpQueue(Long bookId);
}
