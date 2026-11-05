package com.library.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付创建结果
 */
@Data
public class PayVO {

    /** 支付单号 */
    private String orderNo;

    /** 应付金额 */
    private BigDecimal amount;

    /** 支付单状态：0待支付 1已支付 2已关闭 3已全额退款 4部分退款 */
    private Integer status;

    /** 失效时间 */
    private LocalDateTime expireTime;

    /**
     * 支付宝收银台表单 HTML。
     * 前端把它插入页面后浏览器会自动提交跳转到支付宝收银台，
     * 用户在收银台扫码或登录付款。
     */
    private String payForm;
}
