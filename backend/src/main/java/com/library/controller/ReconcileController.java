package com.library.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.AuditLog;
import com.library.common.RequireAdmin;
import com.library.common.Result;
import com.library.entity.ReconcileRecord;
import com.library.service.ReconcileService;
import com.library.vo.ReconcileResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 对账接口（仅管理员）
 */
@RequireAdmin
@RestController
@RequestMapping("/reconcile")
public class ReconcileController {

    @Autowired
    private ReconcileService reconcileService;

    /**
     * 手动触发对账。body 可传 {"date":"2026-11-03"}，不传则对昨天。
     */
    @AuditLog(module = "资金", action = "手动对账")
    @PostMapping("/run")
    public Result<ReconcileResultVO> run(@RequestBody(required = false) Map<String, String> body) {
        LocalDate date = (body == null || body.get("date") == null || body.get("date").isEmpty())
                ? LocalDate.now().minusDays(1)
                : LocalDate.parse(body.get("date"));
        return Result.success("对账完成", reconcileService.reconcile(date));
    }

    /** 差异列表 */
    @GetMapping("/page")
    public Result<Page<ReconcileRecord>> page(@RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "20") Integer size,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) String keyword) {
        return Result.success(reconcileService.page(page, size, status, keyword));
    }

    /** 处理一条差异：resolved=true 已处理，false 忽略 */
    @AuditLog(module = "资金", action = "处理对账差异")
    @PostMapping("/{id}/handle")
    public Result<?> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean resolved = body.get("resolved") == null
                || Boolean.parseBoolean(body.get("resolved").toString());
        String remark = body.get("remark") == null ? null : body.get("remark").toString();
        reconcileService.handle(id, resolved, remark);
        return Result.success("已处理", null);
    }
}
