package com.library.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购买订单视图对象（含图书、用户信息）
 */
@Data
public class PurchaseVO {

    private Long id;
    private Long userId;
    private String userAccount;
    private String userName;
    private Long bookId;
    private String bookName;
    private String bookCover;
    private String orderNo;
    private Integer buyType;
    private BigDecimal price;
    private LocalDateTime createTime;

    /** 订单状态：0待支付 1已支付 2已取消 */
    private Integer status;

    /** 取书状态：0待取书 1已取书（status=1 时有效） */
    private Integer pickupStatus;
    private LocalDateTime pickupTime;

    /** 副本条码（到馆取书时凭它核销） */
    private String barcode;

    /** 展示用状态文案：待支付 / 待取书 / 已取书 / 已取消 */
    private String statusText;

    /** 待取书时是否可取消退款（前端据此决定是否显示按钮） */
    private Boolean cancelable;
}
