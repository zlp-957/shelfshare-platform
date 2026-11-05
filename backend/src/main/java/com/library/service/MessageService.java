package com.library.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.entity.Borrow;
import com.library.entity.Message;
import com.library.mapper.BorrowMapper;
import com.library.mapper.MessageMapper;
import com.library.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 站内信服务
 * <p>
 * 除了「已读 / 未读」这类基础能力，核心是 {@link #scanBorrowReminders(int)}：
 * 借阅到期和逾期是这个业务里最需要触达用户的两件事，而旧版本一条通知都没有——
 * 用户只能自己记着还书，逾期后就地从押金里扣钱，体验上等同于「等着被扣」。
 */
@Slf4j
@Service
public class MessageService {

    /** 站内信类型 */
    public static final int TYPE_DUE = 1;
    public static final int TYPE_OVERDUE = 2;
    public static final int TYPE_RESERVE = 3;
    public static final int TYPE_SYSTEM = 4;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private BorrowMapper borrowMapper;

    /**
     * 发一条站内信（幂等）。
     * <p>
     * 幂等靠 {@code dedupe_key} 的唯一索引：定时任务每小时都扫，
     * 没有这个键同一笔借阅会被提醒 24 次。冲突即代表已发过，直接跳过。
     */
    @Transactional
    public boolean send(Long userId, int type, String title, String content,
                        Long bizId, String dedupeKey) {
        if (userId == null) {
            return false;
        }
        Message msg = new Message();
        msg.setUserId(userId);
        msg.setType(type);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setBizId(bizId);
        msg.setIsRead(0);
        msg.setDedupeKey(dedupeKey);
        msg.setCreateTime(LocalDateTime.now());
        try {
            messageMapper.insert(msg);
            return true;
        } catch (DuplicateKeyException e) {
            // 已经发过，正常情况，不当错误处理
            return false;
        }
    }

    /**
     * 扫描借阅到期与逾期，生成提醒。
     *
     * @param aheadDays 到期前几天开始提醒
     * @return 本次新增的提醒条数
     */
    @Transactional
    public int scanBorrowReminders(int aheadDays) {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;

        // 1) 即将到期：deadline 落在 [now, now + aheadDays]
        List<Borrow> dueList = borrowMapper.selectList(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_BORROWING)
                .ge(Borrow::getDeadlineTime, now)
                .le(Borrow::getDeadlineTime, now.plusDays(aheadDays)));
        for (Borrow borrow : dueList) {
            long remain = ChronoUnit.DAYS.between(now, borrow.getDeadlineTime());
            // 不足一天也按一天提示，避免出现「还剩 0 天」的文案
            remain = Math.max(remain, 0) + 1;
            boolean sent = send(borrow.getUserId(), TYPE_DUE, "图书即将到期",
                    "您借阅的图书还有约 " + remain + " 天到期，请按期归还，逾期将按天从押金中扣除。",
                    borrow.getId(), "DUE:" + borrow.getId());
            if (sent) {
                count++;
            }
        }

        // 2) 已逾期：deadline 已过且仍在借
        List<Borrow> overdueList = borrowMapper.selectList(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_BORROWING)
                .lt(Borrow::getDeadlineTime, now));
        for (Borrow borrow : overdueList) {
            long overdueDays = Math.max(ChronoUnit.DAYS.between(borrow.getDeadlineTime(), now), 1);
            boolean sent = send(borrow.getUserId(), TYPE_OVERDUE, "图书已逾期",
                    "您借阅的图书已逾期 " + overdueDays + " 天，押金正按天扣除，请尽快到馆归还。",
                    borrow.getId(), "OVERDUE:" + borrow.getId());
            if (sent) {
                count++;
            }
            // 顺手把借阅单的逾期标记补上：旧版本只有归还核销时才回写 is_overdue，
            // 导致「借阅中」的列表里看不到任何逾期标识
            borrowMapper.update(null, new LambdaUpdateWrapper<Borrow>()
                    .eq(Borrow::getId, borrow.getId())
                    .eq(Borrow::getStatus, Constants.BORROW_STATUS_BORROWING)
                    .set(Borrow::getIsOverdue, 1));
        }
        return count;
    }

    /** 我的消息（可按已读状态过滤） */
    public List<Message> myMessages(Integer isRead) {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId);
        if (isRead != null) {
            wrapper.eq(Message::getIsRead, isRead);
        }
        wrapper.orderByDesc(Message::getId);
        return messageMapper.selectList(wrapper);
    }

    /** 未读数（顶部红点） */
    public long unreadCount() {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, UserContext.getUserId())
                .eq(Message::getIsRead, 0));
    }

    /** 标记单条已读 */
    public void markRead(Long id) {
        Message msg = messageMapper.selectById(id);
        if (msg == null || !msg.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("消息不存在");
        }
        if (msg.getIsRead() != null && msg.getIsRead() == 1) {
            return;
        }
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, id)
                .set(Message::getIsRead, 1));
    }

    /** 全部标记已读 */
    public void markAllRead() {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getUserId, UserContext.getUserId())
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1));
    }
}
