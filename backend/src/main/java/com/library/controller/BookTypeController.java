package com.library.controller;

import com.library.common.RequireAdmin;
import com.library.common.Result;
import com.library.entity.BookType;
import com.library.service.BookTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 图书分类接口
 */
@RestController
@RequestMapping("/bookType")
public class BookTypeController {

    @Autowired
    private BookTypeService bookTypeService;

    /** 分类列表（读者、管理员都可用） */
    @GetMapping("/list")
    public Result<List<BookType>> list() {
        return Result.success(bookTypeService.listAll());
    }

    @RequireAdmin
    @PostMapping("/add")
    public Result<?> add(@RequestBody BookType bookType) {
        bookTypeService.add(bookType);
        return Result.success("新增成功", null);
    }

    @RequireAdmin
    @PutMapping("/update")
    public Result<?> update(@RequestBody BookType bookType) {
        bookTypeService.update(bookType);
        return Result.success("修改成功", null);
    }

    @RequireAdmin
    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        bookTypeService.delete(id);
        return Result.success("删除成功", null);
    }
}
