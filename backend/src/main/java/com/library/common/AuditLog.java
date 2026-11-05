package com.library.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计标记。
 * <p>
 * 打在 Controller 方法上，由 {@code AuditLogAspect} 统一记录操作人、入参、IP 与结果。
 * 只对「会改变钱或改变他人状态」的写操作使用——不是所有接口都值得记，
 * 否则日志表会被查询请求淹没，真正需要排查时反而翻不到。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 业务模块，如「借阅」 */
    String module();

    /** 具体动作，如「借出核销」 */
    String action();

    /**
     * 是否记录入参，默认记录。
     * <p>
     * 正常情况下<b>不需要</b>改成 false：入参会经过 {@code LogParamMasker} 递归脱敏，
     * 字段名命中 password / token / secret 一类的值会替换成 {@code ***}。
     * 保留入参是为了让日志能回答「改了谁、改了什么」，只留一个操作动作等于半盲。
     * <p>
     * 只有一种情况应该关掉：<b>参数本身就是一段没有字段名的敏感字符串</b>
     * （比如方法签名直接是 {@code updateSecret(String rawPassword)}），
     * 脱敏器按字段名匹配，认不出来。新增审计接口时留意这一点。
     */
    boolean recordArgs() default true;
}
