package com.library.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.AuditLog;
import com.library.common.RequireAdmin;
import com.library.common.Result;
import com.library.entity.Book;
import com.library.service.BookService;
import com.library.service.IsbnLookupService;
import com.library.vo.BookDetailVO;
import com.library.vo.BookVO;
import com.library.vo.IsbnBookVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 图书接口
 */
@RestController
@RequestMapping("/book")
public class BookController {

    @Autowired
    private BookService bookService;

    @Autowired
    private IsbnLookupService isbnLookupService;

    /** 分页查询图书（读者端） */
    @GetMapping("/page")
    public Result<Page<BookVO>> page(@RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "12") Integer size,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Long typeId) {
        return Result.success(bookService.pageBooks(page, size, keyword, typeId));
    }

    /** 图书详情（含当前用户借阅/预约状态） */
    @GetMapping("/{id}")
    public Result<BookDetailVO> detail(@PathVariable Long id) {
        return Result.success(bookService.getDetail(id));
    }

    /** 上传封面（管理员） */
    @RequireAdmin
    @PostMapping("/uploadCover")
    public Result<Map<String, String>> uploadCover(@RequestParam("file") MultipartFile file) {
        String url = bookService.uploadCover(file);
        return Result.success(Map.of("url", url));
    }

    /**
     * 按 ISBN 查图书元数据（管理员，新增图书时自动回填用）
     * <p>
     * 扫码枪本质是键盘输入，扫完回车就会打到这里。
     * 查不到不算错误流程，前端提示「请手动填写」继续录入即可。
     */
    @RequireAdmin
    @GetMapping("/isbn/{isbn}")
    public Result<IsbnBookVO> lookupIsbn(@PathVariable String isbn) {
        return Result.success(isbnLookupService.lookup(isbn));
    }

    /** 新增图书（管理员） */
    @RequireAdmin
    @AuditLog(module = "书目", action = "新增图书")
    @PostMapping("/add")
    public Result<?> add(@RequestBody Book book) {
        bookService.add(book);
        return Result.success("新增成功", null);
    }

    /** 编辑图书（管理员） */
    @RequireAdmin
    @AuditLog(module = "书目", action = "编辑图书")
    @PutMapping("/update")
    public Result<?> update(@RequestBody Book book) {
        bookService.update(book);
        return Result.success("修改成功", null);
    }

    /** 删除图书（管理员） */
    @RequireAdmin
    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        bookService.delete(id);
        return Result.success("删除成功", null);
    }
}
