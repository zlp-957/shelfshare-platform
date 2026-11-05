package com.library.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约记录视图对象（含图书、用户信息、排队位次）
 */
@Data
public class ReserveVO {

    private Long id;
    private Long userId;
    private String userAccount;
    private String userName;
    private Long bookId;
    private String bookName;
    private String bookCover;
    private LocalDateTime reserveTime;
    private Integer status;
    private LocalDateTime validDeadline;
    /** 排队位次（仅排队中状态有效） */
    private Integer queueNo;
}
