package com.library.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ISBN 元数据本地缓存。
 * <p>
 * 第三方图书接口大多有频率限制且不稳定，同一本书重复扫码很常见
 * （采购时一箱书会连扫几十本）。命中缓存既省一次外网往返，
 * 也让上游挂了之后已有书还能继续录入。
 */
@Data
@TableName("isbn_cache")
public class IsbnCache {

    /** ISBN-13（去掉连字符）作为主键，天然去重 */
    @TableId
    private String isbn;

    private String title;

    private String author;

    private String publisher;

    private String cover;

    /** 原始响应，便于上游字段变更后回溯；不参与业务 */
    private String raw;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
