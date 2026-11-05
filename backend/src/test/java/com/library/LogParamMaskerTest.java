package com.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.util.LogParamMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计日志脱敏的回归测试。
 * <p>
 * 这是一个纯单元测试，不连数据库、不启动 Spring，所以默认（mvn test）就会执行。
 * <p>
 * 背景：审计功能刚上线时，管理员「修改读者资料」接口的入参被原样写进
 * {@code operation_log.detail}，落库内容是
 * {@code [2,{"password":"SecretPass123","phone":""}]}——
 * 明文密码被永久留痕。这类问题只要复现一次就够吓人了，必须有测试锁住。
 */
class LogParamMaskerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("顶层 Map 里的 password 必须被打码")
    void maskTopLevelPassword() {
        Map<String, Object> body = new HashMap<>();
        body.put("password", "SecretPass123");
        body.put("phone", "13800000000");

        String json = LogParamMasker.toSafeJson(mapper, new Object[]{2L, body});

        assertFalse(json.contains("SecretPass123"), "明文密码不应出现在审计日志里，实际：" + json);
        assertTrue(json.contains("\"password\":\"***\""), "password 应被替换为 ***，实际：" + json);
        assertTrue(json.contains("13800000000"), "非敏感字段应保留原值，否则日志失去排查价值");
        assertTrue(json.contains("[2,"), "目标 id 必须保留，否则查不到「改了谁」：" + json);
    }

    @Test
    @DisplayName("改密类接口：脱敏后仍能还原操作现场（目标 id + 改动字段）")
    void maskedArgsStillIdentifyTarget() {
        // 对应 UserController.updateReader(Long id, Map body) 的真实入参形状
        Map<String, Object> body = new HashMap<>();
        body.put("password", "TestAudit123");

        String json = LogParamMasker.toSafeJson(mapper, new Object[]{2L, body});

        // 只关掉入参记录（recordArgs=false）也能做到「密码不落库」，但会丢掉前两项信息：
        // 这条用例锁住的是「脱敏而不是失明」——安全与可追溯性同时要
        assertTrue(json.contains("[2,"), "应保留被操作读者 id：" + json);
        assertTrue(json.contains("\"password\":\"***\""), "密码应打码：" + json);
        assertFalse(json.contains("TestAudit123"), "明文密码不得出现：" + json);
    }

    @Test
    @DisplayName("大小写变体与相近字段名同样要打码")
    void maskVariantKeys() {
        Map<String, Object> body = new HashMap<>();
        body.put("PassWord", "A");
        body.put("old_pwd", "B");
        body.put("accessToken", "C");
        body.put("privateKey", "D");
        body.put("username", "zhang");

        String json = LogParamMasker.toSafeJson(mapper, new Object[]{body});

        assertFalse(json.contains("\"A\""), "PassWord 应被打码：" + json);
        assertFalse(json.contains("\"B\""), "old_pwd 应被打码：" + json);
        assertFalse(json.contains("\"C\""), "accessToken 应被打码：" + json);
        assertFalse(json.contains("\"D\""), "privateKey 应被打码：" + json);
        assertTrue(json.contains("zhang"), "username 是正常字段，不该被误伤：" + json);
    }

    @Test
    @DisplayName("嵌套对象与数组里的密码同样要打码")
    void maskNestedStructure() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("password", "nested-secret");
        Map<String, Object> outer = new HashMap<>();
        outer.put("user", inner);
        outer.put("list", new Object[]{inner});

        String json = LogParamMasker.toSafeJson(mapper, new Object[]{outer});

        assertFalse(json.contains("nested-secret"), "嵌套结构里的密码也必须打码：" + json);
    }

    @Test
    @DisplayName("空入参返回 null，不写无意义的空数组")
    void emptyArgs() {
        org.junit.jupiter.api.Assertions.assertNull(LogParamMasker.toSafeJson(mapper, new Object[]{}));
        org.junit.jupiter.api.Assertions.assertNull(LogParamMasker.toSafeJson(mapper, null));
    }
}
