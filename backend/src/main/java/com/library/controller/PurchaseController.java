package com.library.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.RequireAdmin;
import com.library.common.Result;
import com.library.service.PurchaseService;
import com.library.vo.PayVO;
import com.library.vo.PurchaseVO;
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
 * 购买接口
 */
@RestController
@RequestMapping("/purchase")
public class PurchaseController {

    @Autowired
    private PurchaseService purchaseService;

    /** 购买图书：锁定一册副本并返回收银台表单 */
    @PostMapping("/buy/{bookId}")
    public Result<PayVO> buy(@PathVariable Long bookId) {
        return Result.success("请完成支付", purchaseService.buy(bookId));
    }

    /** 我的订单 */
    @GetMapping("/my")
    public Result<List<PurchaseVO>> my() {
        return Result.success(purchaseService.myOrders());
    }

    /** 用户取消待取书订单（全额退书款，副本回馆藏） */
    @PostMapping("/cancel/{orderId}")
    public Result<?> cancel(@PathVariable Long orderId) {
        purchaseService.cancelPending(orderId);
        return Result.success("订单已取消，书款将原路退回", null);
    }

    /** 管理员：扫副本条码核销取书（待取书 -> 已取书） */
    @RequireAdmin
    @PostMapping("/pickup")
    public Result<PurchaseVO> pickup(@RequestBody Map<String, String> body) {
        PurchaseVO vo = purchaseService.pickup(body.get("barcode"));
        return Result.success("取书核销成功", vo);
    }

    /** 管理员：分页查询订单 */
    @RequireAdmin
    @GetMapping("/page")
    public Result<Page<PurchaseVO>> page(@RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "10") Integer size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer buyType) {
        return Result.success(purchaseService.pageOrders(page, size, keyword, buyType));
    }
}
