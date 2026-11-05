package com.library.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.RequireAdmin;
import com.library.common.Result;
import com.library.service.ReserveService;
import com.library.vo.ReserveVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 图书预约接口
 */
@RestController
@RequestMapping("/reserve")
public class ReserveController {

    @Autowired
    private ReserveService reserveService;

    /** 提交预约 */
    @PostMapping("/add")
    public Result<?> reserve(@RequestBody Map<String, Long> body) {
        reserveService.reserve(body.get("bookId"));
        return Result.success("预约成功，已进入排队队列", null);
    }

    /** 取消预约 */
    @PostMapping("/cancel/{id}")
    public Result<?> cancel(@PathVariable Long id) {
        reserveService.cancel(id);
        return Result.success("取消成功", null);
    }

    /** 我的预约 */
    @GetMapping("/my")
    public Result<List<ReserveVO>> my() {
        return Result.success(reserveService.myReserves());
    }

    /** 管理员：分页查询预约 */
    @RequireAdmin
    @GetMapping("/page")
    public Result<Page<ReserveVO>> page(@RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "10") Integer size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer status) {
        return Result.success(reserveService.pageReserves(page, size, keyword, status));
    }

    /** 管理员：手动处理失效预约 */
    @RequireAdmin
    @PostMapping("/processExpired")
    public Result<?> processExpired() {
        reserveService.processExpired();
        return Result.success("处理完成", null);
    }
}
