package com.library.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 审计日志入参脱敏。
 * <p>
 * 审计日志是「永久留痕」的：一旦把明文密码写进去，数据库备份、日志导出、
 * 运维排查都可能把密码带出去，而且删不掉（日志本该只追加不改）。
 * <p>
 * 所以脱敏不能依赖调用方记得加 {@code recordArgs = false}——
 * 那是「要求每个人都做对」的设计，早晚会漏。这里做成默认行为：
 * 字段名命中 password / token 一类的，值一律替换成 ***，嵌套结构递归处理。
 */
public final class LogParamMasker {

    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i)(password|passwd|pwd|secret|token|privatekey|private_key|credential)");

    private LogParamMasker() {
    }

    /**
     * 把入参序列化成脱敏后的 JSON
     */
    public static String toSafeJson(ObjectMapper mapper, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> safe = new ArrayList<>();
        for (Object arg : args) {
            safe.add(describe(arg));
        }
        try {
            JsonNode tree = mapper.valueToTree(safe);
            mask(tree);
            return mapper.writeValueAsString(tree);
        } catch (Exception e) {
            return "[入参无法序列化: " + e.getClass().getSimpleName() + "]";
        }
    }

    /**
     * 非结构化对象先转成可读描述：文件流不能直接序列化，会抛异常或写出巨量 base64
     */
    private static Object describe(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof org.springframework.web.multipart.MultipartFile) {
            org.springframework.web.multipart.MultipartFile f =
                    (org.springframework.web.multipart.MultipartFile) arg;
            return "[文件] " + f.getOriginalFilename() + " (" + f.getSize() + " bytes)";
        }
        if (arg instanceof javax.servlet.http.HttpServletRequest) {
            return "[HttpServletRequest]";
        }
        return arg;
    }

    /**
     * 递归脱敏：对象按字段名判断，数组逐元素下钻
     */
    public static void mask(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode) {
            ObjectNode obj = (ObjectNode) node;
            // 先收集字段名再改值：边遍历边 put 会抛 ConcurrentModificationException
            List<String> names = new ArrayList<>();
            Iterator<String> it = obj.fieldNames();
            while (it.hasNext()) {
                names.add(it.next());
            }
            for (String name : names) {
                if (SENSITIVE_KEY.matcher(name).find()) {
                    obj.put(name, "***");
                } else {
                    mask(obj.get(name));
                }
            }
        } else if (node instanceof ArrayNode) {
            for (JsonNode child : node) {
                mask(child);
            }
        }
    }
}
