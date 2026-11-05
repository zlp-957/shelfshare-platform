package com.library.controller;

import com.library.common.Result;
import com.library.service.CartService;
import com.library.vo.CartItemVO;
import com.library.vo.PayVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 购物车接口（合并结算）
 */
@RestController
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    /** 合并结算：一次支付完成多本借阅/购买 */
    @PostMapping("/checkout")
    public Result<PayVO> checkout(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        List<CartItemVO> items = new java.util.ArrayList<>();
        if (rawItems != null) {
            for (Map<String, Object> raw : rawItems) {
                CartItemVO vo = new CartItemVO();
                vo.setBookId(raw.get("bookId") == null ? null : Long.valueOf(raw.get("bookId").toString()));
                vo.setMode(raw.get("mode") == null ? "borrow" : raw.get("mode").toString());
                items.add(vo);
            }
        }
        return Result.success("请完成支付", cartService.checkout(items));
    }
}
