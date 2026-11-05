package com.library.controller;

import com.library.common.Result;
import com.library.service.AuthService;
import com.library.vo.LoginVO;
import com.library.vo.SelfRegisterVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody LoginVO vo) {
        return Result.success(authService.login(vo));
    }

    /**
     * 读者自助注册：账号由系统分配，返回给前端展示给用户
     */
    @PostMapping("/register")
    public Result<Map<String, String>> register(@Validated @RequestBody SelfRegisterVO vo) {
        String account = authService.register(vo);
        return Result.success("注册成功", Map.of("account", account));
    }
}
