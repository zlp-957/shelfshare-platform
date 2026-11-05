package com.library.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.AuditLog;
import com.library.common.RequireAdmin;
import com.library.common.Result;
import com.library.service.BorrowService;
import com.library.vo.BorrowSettleVO;
import com.library.vo.BorrowVO;
import com.library.vo.PayVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 借阅接口（押金制 + 到馆核销）
 */
@RestController
@RequestMapping("/borrow")
public class BorrowController {

    @Autowired
    private BorrowService borrowService;

    /**
     * 申请借阅：锁定一册副本 + 生成押金支付单，返回支付宝收银台表单
     */
    @PostMapping("/apply")
    public Result<PayVO> apply(@RequestBody Map<String, Object> body) {
        Long bookId = body.get("bookId") == null ? null : Long.valueOf(body.get("bookId").toString());
        return Result.success("请完成押金支付", borrowService.apply(bookId));
    }

    /** 用户取消待取书的借阅（已付押金全额退；未付款的只关单，不产生退款） */
    @AuditLog(module = "借阅", action = "用户取消借阅")
    @PostMapping("/cancel/{borrowId}")
    public Result<?> cancel(@PathVariable Long borrowId) {
        borrowService.cancel(borrowId);
        // 这里不写「押金将原路退回」——未支付的单本来就没付过钱，
        // 到底退不退由前端按支付状态决定文案
        return Result.success("已取消", null);
    }

    /**
     * 管理员：借出核销
     * 扫副本条码 + 读者账号
     */
    @RequireAdmin
    @AuditLog(module = "借阅", action = "借出核销")
    @PostMapping("/checkOut")
    public Result<BorrowSettleVO> checkOut(@RequestBody Map<String, String> body) {
        BorrowSettleVO vo = borrowService.checkOut(body.get("barcode"), body.get("readerAccount"));
        return Result.success("借出成功", vo);
    }

    /**
     * 管理员：归还核销
     * 扫副本条码即可，可附带破损赔偿金额
     */
    @RequireAdmin
    @AuditLog(module = "借阅", action = "归还核销")
    @PostMapping("/checkIn")
    public Result<BorrowSettleVO> checkIn(@RequestBody Map<String, Object> body) {
        String barcode = body.get("barcode") == null ? null : body.get("barcode").toString();
        BigDecimal damageFee = body.get("damageFee") == null ? BigDecimal.ZERO
                : new BigDecimal(body.get("damageFee").toString());
        BorrowSettleVO vo = borrowService.checkIn(barcode, damageFee);
        return Result.success("归还成功", vo);
    }

    /** 我的借阅 */
    @GetMapping("/my")
    public Result<List<BorrowVO>> my(@RequestParam(required = false) Integer status) {
        return Result.success(borrowService.myBorrows(status));
    }

    /** 管理员：分页查询借阅记录 */
    @RequireAdmin
    @GetMapping("/page")
    public Result<Page<BorrowVO>> page(@RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer status) {
        return Result.success(borrowService.pageBorrows(page, size, keyword, status));
    }

    /** 管理员：手动触发逾期处理 */
    @RequireAdmin
    @PostMapping("/processOverdue")
    public Result<?> processOverdue() {
        borrowService.processOverdue();
        return Result.success("逾期处理完成", null);
    }
}
