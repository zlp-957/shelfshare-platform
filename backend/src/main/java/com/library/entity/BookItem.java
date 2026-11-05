package com.library.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图书副本表
 * <p>
 * 每册实体书一条记录，拥有独立条码。
 * 这是扫码核销能够成立的前提：ISBN 只能标识「一种书」，副本条码才能标识「某一册」。
 */
@Data
@TableName("book_item")
public class BookItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属书目id */
    private Long bookId;

    /** 副本条码（馆内唯一，贴在书内页） */
    private String barcode;

    /** 状态：0在馆 1借出中 2已售出 3已买断 4遗失 5破损 6下架 7已预留 */
    private Integer status;

    /** 馆内位置 */
    private String location;

    private String remark;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
