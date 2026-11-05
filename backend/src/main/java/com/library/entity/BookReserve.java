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
 * 图书预约表
 */
@Data
@TableName("book_reserve")
public class BookReserve {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long bookId;

    /** 预约时间（FIFO 排队依据） */
    private LocalDateTime reserveTime;

    /** 状态：0排队中 1待借阅 2已借阅 3已取消 4已失效 */
    private Integer status;

    /** 通知后有效截止时间（待借阅状态下3天内需借阅） */
    private LocalDateTime validDeadline;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
