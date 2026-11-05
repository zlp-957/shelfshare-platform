package com.library.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.RequireAdmin;
import com.library.common.Result;
import com.library.entity.Notice;
import com.library.service.NoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 公告接口
 */
@RestController
@RequestMapping("/notice")
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    /** 公告分页（读者、管理员都可用） */
    @GetMapping("/page")
    public Result<Page<Notice>> page(@RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(noticeService.pageNotices(page, size));
    }

    /** 最新公告（首页展示） */
    @GetMapping("/latest")
    public Result<List<Notice>> latest() {
        return Result.success(noticeService.latestNotices());
    }

    @RequireAdmin
    @PostMapping("/add")
    public Result<?> add(@RequestBody Notice notice) {
        noticeService.add(notice);
        return Result.success("发布成功", null);
    }

    @RequireAdmin
    @PutMapping("/update")
    public Result<?> update(@RequestBody Notice notice) {
        noticeService.update(notice);
        return Result.success("修改成功", null);
    }

    @RequireAdmin
    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.success("删除成功", null);
    }
}
