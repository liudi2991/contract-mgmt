package com.company.contract.audit;

import com.company.contract.domain.entity.AuditLog;
import com.company.contract.mapper.AuditLogMapper;
import com.company.contract.security.SecurityHelper;
import com.company.contract.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint pjp, Audit audit) throws Throwable {
        String ip = currentIp();
        UserPrincipal user = SecurityHelper.currentUserOrNull();
        try {
            Object result = pjp.proceed();
            saveAsync(audit, user, ip, "SUCCESS", null, result);
            return result;
        } catch (Throwable t) {
            saveAsync(audit, user, ip, "FAIL", t.getMessage(), null);
            throw t;
        }
    }

    @Async("auditLogExecutor")
    void saveAsync(Audit audit, UserPrincipal user, String ip, String result, String errMsg, Object resp) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(user == null ? null : user.getId());
            log.setUsername(user == null ? null : user.getUsername());
            log.setIp(ip);
            log.setModule(audit.module());
            log.setAction(audit.action());
            log.setTargetType(audit.targetType().isEmpty() ? null : audit.targetType());
            log.setResult(result);
            log.setErrorMsg(errMsg);
            log.setCreatedAt(LocalDateTime.now());
            if (resp != null) {
                try {
                    log.setAfterJson(objectMapper.writeValueAsString(resp));
                } catch (Exception ignore) {}
            }
            auditLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("save audit log fail: {}", e.getMessage());
        }
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
