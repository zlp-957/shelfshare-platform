package com.library.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 图书详情视图对象（含当前用户对该书的借阅/预约状态）
 * <p>
 * 前端根据 userStatus 决定按钮形态，避免「点预约没反应」「借了还点借阅」这类重复操作。
 */
@Data
public class BookDetailVO {

    // ===== 书目基本信息（与 BookVO 一致） =====
    private Long id;
    private String name;
    private String author;
    private String isbn;
    private Long typeId;
    private String typeName;
    private BigDecimal price;
    private Integer stock;
    private String cover;
    private String intro;

    // ===== 当前用户状态 =====

    /**
     * 用户对该书的借阅/预约进行态（只描述「正在进行的事」，不含购买）：
     * 0 无
     * 1 借阅中
     * 2 待取书（已付押金，等核销）
     * 3 待支付（已下单未付押金）
     * 4 已预约（排队中）
     * 5 已预约（已通知待借）
     */
    private Integer userStatus;

    /**
     * 是否购买过该书。
     * <p>
     * 独立字段，不参与 userStatus 互斥：买过只是既成事实，
     * 读者仍可以继续从馆藏借阅，也可以再买一本。前端只用它显示一个标记。
     */
    private Boolean purchased;

    /** 预约排队位次（userStatus=4 时有效） */
    private Integer queueNo;

    /** 该书是否存在其他读者的有效预约（用于「已预约满」提示） */
    private Boolean reservedByOthers;
}
