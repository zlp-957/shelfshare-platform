package com.library.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 副本视图
 */
@Data
public class BookItemVO {

    private Long id;

    private Long bookId;

    /** 书名（冗余，便于核销页展示） */
    private String bookName;

    /** 副本条码 */
    private String barcode;

    /** 状态：0在馆 1借出中 2已售出 3已买断 4遗失 5破损 6下架 7已预留 */
    private Integer status;

    /** 状态文字 */
    private String statusText;

    private String location;

    private String remark;

    private LocalDateTime createTime;
}
