package com.library.controller;

import com.library.common.RequireAdmin;
import com.library.common.Result;
import com.library.service.StatService;
import com.library.vo.StatVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计接口
 */
@RestController
@RequestMapping("/stat")
public class StatController {

    @Autowired
    private StatService statService;

    @RequireAdmin
    @GetMapping("/overview")
    public Result<StatVO> overview() {
        return Result.success(statService.stat());
    }
}
