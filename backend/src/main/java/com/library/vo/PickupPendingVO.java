package com.library.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 取书待办清单（管理端核销页）
 * <p>
 * 输入取书码或读者账号后返回：这个人要取的借阅书 + 买下的书，一次看全。
 */
@Data
public class PickupPendingVO {

    private Long userId;
    private String userAccount;
    private String userName;
    private String phone;

    /** 若本次是按取书码查到的，回显该码 */
    private String pickupCode;
    private LocalDateTime codeExpire;

    /** 待取书的借阅（押金已付，等核销） */
    private List<BorrowVO> borrows;

    /** 待取书的购买（已付款，等核销） */
    private List<PurchaseVO> purchases;

    /** 合计待取件数 */
    private Integer totalCount;
}
