package com.library.controller;

import com.library.common.Result;
import com.library.entity.Message;
import com.library.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内信接口（读者端）
 */
@RestController
@RequestMapping("/message")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @GetMapping("/my")
    public Result<List<Message>> my(@RequestParam(required = false) Integer isRead) {
        return Result.success(messageService.myMessages(isRead));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(messageService.unreadCount());
    }

    @PostMapping("/{id}/read")
    public Result<?> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.success("已读", null);
    }

    @PostMapping("/read-all")
    public Result<?> markAllRead() {
        messageService.markAllRead();
        return Result.success("全部已读", null);
    }
}
