package com.library.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.RequireAdmin;
import com.library.common.Result;
import com.library.service.PasswordResetService;
import com.library.vo.PasswordResetVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 密码找回接口
 * <p>
 * apply / reset 为读者自助使用，不需要登录（用户正是登不进去才来的）；
 * page / generate / reject 仅管理员可用。
 */
@RestController
@RequestMapping("/password-reset")
public class PasswordResetController {

    @Autowired
    private PasswordResetService passwordResetService;

    /** 读者提交找回申请：姓名 + 手机号须与账号绑定信息一致 */
    @PostMapping("/apply")
    public Result<?> apply(@RequestBody Map<String, String> body) {
        passwordResetService.apply(body.get("name"), body.get("phone"));
        return Result.success("申请已提交，工作人员核实后会电话联系你", null);
    }

    /** 读者凭重置码设置新密码 */
    @PostMapping("/reset")
    public Result<?> reset(@RequestBody Map<String, String> body) {
        passwordResetService.resetPassword(
                body.get("account"), body.get("code"), body.get("newPassword"));
        return Result.success("密码已重置，请用新密码登录", null);
    }

    /** 管理员：申请列表 */
    @RequireAdmin
    @GetMapping("/page")
    public Result<Page<PasswordResetVO>> page(@RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "10") Integer size,
                                              @RequestParam(required = false) Integer status) {
        return Result.success(passwordResetService.page(page, size, status));
    }

    /** 管理员：待处理数量（菜单角标） */
    @RequireAdmin
    @GetMapping("/pendingCount")
    public Result<Long> pendingCount() {
        return Result.success(passwordResetService.pendingCount());
    }

    /** 管理员：核实后发放一次性重置码（只返回这一次，请口头告知读者） */
    @RequireAdmin
    @PostMapping("/{id}/generate")
    public Result<Map<String, String>> generate(@PathVariable Long id) {
        String code = passwordResetService.generateCode(id);
        return Result.success("重置码已生成，请告知读者", Map.of("code", code));
    }

    /** 管理员：拒绝申请 */
    @RequireAdmin
    @PostMapping("/{id}/reject")
    public Result<?> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        passwordResetService.reject(id, body == null ? null : body.get("remark"));
        return Result.success("已拒绝该申请", null);
    }
}
