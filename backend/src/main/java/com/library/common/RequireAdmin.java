package com.library.common;

import java.lang.annotation.*;

/**
 * 标记需要管理员权限的接口
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
