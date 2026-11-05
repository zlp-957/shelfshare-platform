package com.library.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页参数规范化：防止 page=0 / 负数、size 过大导致的异常与资源滥用
 */
public final class PageUtil {

    /** 单页最大条数 */
    public static final int MAX_SIZE = 100;
    /** 默认单页条数 */
    public static final int DEFAULT_SIZE = 10;

    private PageUtil() {
    }

    public static int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    public static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /**
     * 构建已规范化的分页对象
     */
    public static <T> Page<T> of(Integer page, Integer size) {
        return new Page<>(normalizePage(page), normalizeSize(size));
    }
}
