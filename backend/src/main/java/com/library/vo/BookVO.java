package com.library.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 图书列表视图对象（含分类名）
 */
@Data
public class BookVO {

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
    private LocalDateTime createTime;
}
