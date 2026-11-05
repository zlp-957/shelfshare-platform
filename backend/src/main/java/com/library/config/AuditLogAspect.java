package com.library.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.common.AuditLog;
import com.library.entity.OperationLog;
import com.library.service.OperationLogService;
import com.library.util.ClientIpResolver;
import com.library.util.LogParamMasker;
import com.library.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 操作审计切面。
 * <p>
 * 三个刻意的设计：
 * <ol>
 *   <li><b>失败也记</b>。业务抛异常时先落日志再把异常原样抛出去，
 *       不能因为「记日志」改变接口本身的返回；</li>
 *   <li><b>日志写入异常不能影响业务</b>。整个写入包在 try-catch 里，
 *       审计是旁路能力，不能成为新的故障点；</li>
 *   <li><b>入参要脱敏和截断</b>。文件流不能序列化，长文本会撑爆列宽。</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 入参摘要最大长度，超长截断（detail 列 varchar(1000)） */
    private static final int MAX_DETAIL_LENGTH = 900;

    /** 异常信息最大长度（error_msg 列 varchar(255)） */
    private static final int MAX_ERROR_LENGTH = 240;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        boolean success = true;
        String errorMsg = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            success = false;
            errorMsg = t.getMessage();
            throw t;
        } finally {
            try {
                writeLog(pjp, auditLog, success, errorMsg);
            } catch (Exception e) {
                // 审计失败不影响业务结果，只留一条应用日志
                log.warn("写操作审计日志失败 module={} action={}", auditLog.module(), auditLog.action(), e);
            }
        }
    }

    private void writeLog(ProceedingJoinPoint pjp, AuditLog auditLog, boolean success, String errorMsg) {
        OperationLog entity = new OperationLog();
        entity.setUserId(UserContext.getUserId());
        entity.setUserAccount(UserContext.getAccount());
        entity.setModule(auditLog.module());
        entity.setAction(auditLog.action());
        entity.setIp(resolveIp());
        entity.setSuccess(success ? 1 : 0);
        if (errorMsg != null) {
            entity.setErrorMsg(truncate(errorMsg, MAX_ERROR_LENGTH));
        }
        if (auditLog.recordArgs()) {
            entity.setDetail(truncate(serializeArgs(pjp.getArgs()), MAX_DETAIL_LENGTH));
        }
        operationLogService.save(entity);
    }

    private String resolveIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            return clientIpResolver.resolve(request);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 序列化入参。跳过文件流和原生请求对象，其余转成 JSON 便于事后还原现场。
     * <p>
     * 脱敏在 {@link LogParamMasker} 里统一做：字段名命中 password / token 的一律替换成 ***。
     * 这一步不能省——管理员重置读者密码的入参里就带着明文新密码。
     */
    private String serializeArgs(Object[] args) {
        return LogParamMasker.toSafeJson(MAPPER, args);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
