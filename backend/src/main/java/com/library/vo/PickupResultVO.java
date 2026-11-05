package com.library.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量取书核销结果
 * <p>
 * 支持部分成功：勾选 5 本，其中 4 本找到书核销掉，剩 1 本缺货没给，
 * 成功的照常生效，失败的在 failures 里说明原因。
 */
@Data
public class PickupResultVO {

    private Integer borrowSuccess = 0;
    private Integer purchaseSuccess = 0;

    /** 失败条目说明，如「《三体》该副本不在待核销状态」 */
    private List<String> failures = new ArrayList<>();

    /** 本次核销后是否已作废取书码 */
    private Boolean codeInvalidated = false;
}
