package com.library.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信。
 * <p>
 * 系统里所有「应该通知用户」的事件都落这张表：借阅到期、逾期、预约到书。
 * 目前只做站内触达，但表结构留了 type 和 dedupe_key，
 * 之后接短信/邮件只需要加一个投递渠道，业务侧不用改。
 */
@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 类型：1 到期提醒 2 逾期提醒 3 预约到书 4 系统公告 */
    private Integer type;

    private String title;

    private String content;

    /** 关联业务 id（如 borrow.id），便于前端跳转 */
    private Long bizId;

    /** 是否已读：0 未读 1 已读 */
    private Integer isRead;

    /**
     * 幂等键（唯一索引）。
     * <p>
     * 定时任务每小时都会扫一遍，没有这个键同一笔借阅会被提醒 24 次。
     * 形如 {@code DUE:12} / {@code OVERDUE:12}，插入冲突即代表已发过。
     */
    private String dedupeKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
