package com.library.vo;

import lombok.Data;

/**
 * 购物车结算条目
 */
@Data
public class CartItemVO {

    private Long bookId;

    /**
     * 结算类型：borrow 借阅（付押金）、buy 购买（付书款）
     */
    private String mode;
}
