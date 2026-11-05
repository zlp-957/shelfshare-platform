package com.library.controller;

import com.library.common.RequireAdmin;
import com.library.common.Result;
import com.library.service.PickupService;
import com.library.util.ClientIpResolver;
import com.library.vo.PickupPendingVO;
import com.library.vo.PickupResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 取书接口
 * <p>
 * 读者端：生成取书码 / 查看自己的码。
 * 管理端：按码或账号查待取清单 / 勾选批量核销。
 */
@RestController
@RequestMapping("/pickup")
public class PickupController {

    @Autowired
    private PickupService pickupService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    /** 读者：生成（或刷新）取书码，旧码立即失效 */
    @PostMapping("/code")
    public Result<Map<String, Object>> generateCode() {
        String code = pickupService.generateCode();
        return Result.success("取书码已生成",
                Map.of("code", code, "expireMinutes", 30));
    }

    /** 读者：查看当前有效的取书码 */
    @GetMapping("/code")
    public Result<PickupPendingVO> myCode() {
        return Result.success(pickupService.myCodeInfo());
    }

    /** 管理员：按取书码或读者账号查询待取清单 */
    @RequireAdmin
    @GetMapping("/pending")
    public Result<PickupPendingVO> pending(@RequestParam String keyword,
                                           HttpServletRequest request) {
        return Result.success(pickupService.pending(keyword, clientIpResolver.resolve(request)));
    }

    /** 管理员：勾选后批量核销（支持部分成功） */
    @RequireAdmin
    @PostMapping("/do")
    public Result<PickupResultVO> doPickup(@RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        String keyword = body.get("keyword") == null ? null : body.get("keyword").toString();
        List<Long> borrowIds = toLongList(body.get("borrowIds"));
        List<Long> orderIds = toLongList(body.get("orderIds"));
        return Result.success("核销完成",
                pickupService.pickup(keyword, borrowIds, orderIds,
                        clientIpResolver.resolve(request)));
    }

    private List<Long> toLongList(Object raw) {
        List<Long> out = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o != null) {
                    out.add(Long.valueOf(o.toString()));
                }
            }
        }
        return out;
    }
}
