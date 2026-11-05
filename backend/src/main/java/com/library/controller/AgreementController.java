package com.library.controller;

import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.common.Result;
import com.library.entity.UserAgreement;
import com.library.service.AgreementService;
import com.library.util.ClientIpResolver;
import com.library.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则确认接口
 */
@RestController
@RequestMapping("/agreement")
public class AgreementController {

    @Autowired
    private AgreementService agreementService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    /**
     * 获取规则正文与版本
     * type: 1借阅规则 2购买须知
     */
    @GetMapping("/current")
    public Result<Map<String, Object>> current(@RequestParam(defaultValue = "1") Integer type) {
        Map<String, Object> data = new HashMap<>();
        data.put("version", agreementService.currentVersion());
        data.put("content", agreementService.loadContent(type));
        Long userId = UserContext.getUserId();
        data.put("agreed", agreementService.hasAgreed(userId, type));
        return Result.success(data);
    }

    /**
     * 记录同意
     */
    @PostMapping("/agree")
    public Result<?> agree(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        int type = body.get("type") == null
                ? Constants.AGREEMENT_TYPE_BORROW
                : Integer.parseInt(body.get("type").toString());
        // 白名单校验：不能只信前端传的值，否则 type=99 也会被写入一条无意义的同意记录
        if (type != Constants.AGREEMENT_TYPE_BORROW
                && type != Constants.AGREEMENT_TYPE_PURCHASE
                && type != Constants.AGREEMENT_TYPE_USER) {
            throw new BusinessException("不支持的协议类型");
        }
        agreementService.agree(UserContext.getUserId(), type, clientIpResolver.resolve(request));
        return Result.success("已记录", null);
    }

    /**
     * 我的同意记录（版本、时间、IP）
     */
    @GetMapping("/mine")
    public Result<List<UserAgreement>> mine() {
        return Result.success(agreementService.myAgreements(UserContext.getUserId()));
    }
}
