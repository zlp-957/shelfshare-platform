package com.library.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.RequireAdmin;
import com.library.common.AuditLog;
import com.library.common.Result;
import com.library.entity.User;
import com.library.service.UserService;
import com.library.util.FileUtil;
import com.library.vo.RegisterVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private FileUtil fileUtil;

    /** 当前登录用户信息 */
    @GetMapping("/info")
    public Result<User> info() {
        return Result.success(userService.getCurrentUser());
    }

    /** 修改个人信息 */
    @PostMapping("/profile")
    public Result<?> updateProfile(@RequestBody Map<String, String> body) {
        userService.updateProfile(body.get("name"));
        return Result.success();
    }

    /** 修改密码 */
    @PostMapping("/password")
    public Result<?> updatePassword(@RequestBody Map<String, String> body) {
        userService.updatePassword(body.get("oldPassword"), body.get("newPassword"));
        return Result.success("密码修改成功", null);
    }

    /** 上传头像，返回头像 URL */
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String url = fileUtil.saveCover(file);
        userService.updateAvatar(url);
        return Result.success(Map.of("url", url));
    }

    /** 修改手机号 */
    @PostMapping("/phone")
    public Result<?> updatePhone(@RequestBody Map<String, String> body) {
        userService.updatePhone(body.get("phone"));
        return Result.success("手机号已更新", null);
    }

    /** 管理员：分页查询读者 */
    @RequireAdmin
    @GetMapping("/readers")
    public Result<Page<User>> readers(@RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "10") Integer size,
                                      @RequestParam(required = false) String keyword) {
        return Result.success(userService.pageReaders(page, size, keyword));
    }

    /**
     * 管理员：新增读者
     * <p>
     * 后台已不再提供入口：读者账号由系统在自助注册时统一分配（R + 年份 + 4 位序号），
     * 管理员手工建号容易破坏这套编号规则。接口保留仅供运维/初始化脚本使用。
     */
    @RequireAdmin
    @PostMapping("/reader")
    public Result<?> addReader(@Validated @RequestBody RegisterVO vo) {
        userService.addReader(vo);
        return Result.success("新增读者成功", null);
    }

    /**
     * 管理员：编辑读者
     * <p>
     * 同样已不在后台提供入口，姓名/手机号由读者在个人中心自行维护。
     */
    @RequireAdmin
    // 入参照记，但 password 会被 LogParamMasker 打码成 ***。
    // 曾经这里整条关掉过入参（recordArgs = false），安全是安全了，
    // 代价是日志只剩「谁改了读者」，查不到「改了谁、改了什么」——
    // 审计日志的意义一半就没了。正确做法是脱敏而不是失明。
    // 落库效果：[2,{"password":"***","phone":""}]，目标 id 与改动字段都还在
    @AuditLog(module = "读者", action = "修改读者资料")
    @PutMapping("/reader/{id}")
    public Result<?> updateReader(@PathVariable Long id,
                                  @RequestBody Map<String, String> body) {
        // phone 不传 = 不修改；传空串 = 清空
        userService.updateReader(id, body.get("name"), body.get("password"), body.get("phone"));
        return Result.success("编辑成功", null);
    }

    /** 管理员：启用/禁用读者 */
    @RequireAdmin
    @AuditLog(module = "读者", action = "启用/禁用读者")
    @PostMapping("/reader/{id}/toggle")
    public Result<?> toggleStatus(@PathVariable Long id) {
        userService.toggleStatus(id);
        return Result.success();
    }
}
