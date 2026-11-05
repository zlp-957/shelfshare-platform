package com.library.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.RequireAdmin;
import com.library.common.AuditLog;
import com.library.common.Result;
import com.library.service.BookItemService;
import com.library.vo.BookItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 图书副本接口
 */
@RestController
@RequestMapping("/bookItem")
public class BookItemController {

    @Autowired
    private BookItemService bookItemService;

    /** 管理员：为某本书新增副本 */
    @RequireAdmin
    @PostMapping("/add")
    public Result<?> add(@RequestBody Map<String, Object> body) {
        Long bookId = body.get("bookId") == null ? null : Long.valueOf(body.get("bookId").toString());
        int count = body.get("count") == null ? 0 : Integer.parseInt(body.get("count").toString());
        String location = body.get("location") == null ? null : body.get("location").toString();
        bookItemService.addItems(bookId, count, location);
        return Result.success("新增副本成功", null);
    }

    /** 某本书的副本列表 */
    @GetMapping("/list/{bookId}")
    public Result<List<BookItemVO>> listByBook(@PathVariable Long bookId) {
        return Result.success(bookItemService.listByBook(bookId));
    }

    /** 管理员：分页查询副本 */
    @RequireAdmin
    @GetMapping("/page")
    public Result<Page<BookItemVO>> page(@RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "10") Integer size,
                                         @RequestParam(required = false) Long bookId,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(bookItemService.page(page, size, bookId, status, keyword));
    }

    /** 管理员：标记副本遗失 / 破损 / 下架 */
    @RequireAdmin
    @AuditLog(module = "馆藏", action = "变更副本状态")
    @PostMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") == null ? null : Integer.valueOf(body.get("status").toString());
        String remark = body.get("remark") == null ? null : body.get("remark").toString();
        bookItemService.updateStatus(id, status, remark);
        return Result.success("状态已更新", null);
    }

    /** 按条码查询（仅管理员，核销页扫码后回显用） */
    @RequireAdmin
    @GetMapping("/barcode/{barcode}")
    public Result<BookItemVO> getByBarcode(@PathVariable String barcode) {
        return Result.success(bookItemService.getByBarcode(barcode));
    }
}
