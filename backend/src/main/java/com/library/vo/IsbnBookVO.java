package com.library.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * ISBN 查询结果（供管理端新增图书时自动回填）
 */
@Data
public class IsbnBookVO {

    private String isbn;

    private String title;

    private String author;

    private String publisher;

    /** 定价。多数公开接口不提供，留空由管理员填写 */
    private BigDecimal price;

    private String cover;

    /**
     * 数据来源：cache（本地缓存）/ remote（第三方接口）
     * 前端可据此提示「该结果来自缓存」
     */
    private String source;

    /** 第三方返回的简介/出版日期等补充信息，可选展示 */
    private String extra;
}
