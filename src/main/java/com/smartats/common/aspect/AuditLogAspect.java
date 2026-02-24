package com.smartats.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.annotation.AuditLog;
import com.smartats.module.audit.entity.AuditLogEntity;
import com.smartats.module.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计日志 AOP 切面
 * <p>
 * 拦截所有标注了 {@link AuditLog} 注解的方法，自动记录操作日志。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>使用 @Around 通知，同时捕获成功和失败场景</li>
 *   <li>日志写入异步执行（{@link AuditLogService#saveAsync}），不阻塞主业务</li>
 *   <li>自动提取当前用户、请求信息、执行耗时</li>
 *   <li>敏感参数自动过滤（密码、Token 等）</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /** 请求参数序列化最大长度（防止日志过大） */
    private static final int MAX_PARAMS_LENGTH = 2000;

    /** 敏感字段名集合（参数名包含这些关键词时脱敏） */
    private static final String[] SENSITIVE_FIELDS = {"password", "secret", "token", "authorization", "credential"};

    /**
     * 切入点：所有标注 @AuditLog 注解的方法
     */
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 预构建审计日志实体（请求信息在方法执行前采集）
        AuditLogEntity entity = buildAuditLogEntity(joinPoint, auditLog);

        Object result;
        try {
            result = joinPoint.proceed();
            entity.setStatus("SUCCESS");
        } catch (Throwable e) {
            entity.setStatus("FAILED");
            entity.setErrorMessage(truncate(e.getMessage(), 500));
            throw e; // 重新抛出，不吞异常
        } finally {
            entity.setDuration(System.currentTimeMillis() - startTime);
            entity.setCreatedAt(LocalDateTime.now());

            // 异步写入数据库
            auditLogService.saveAsync(entity);
        }

        return result;
    }

    /**
     * 构建审计日志实体
     */
    private AuditLogEntity buildAuditLogEntity(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        AuditLogEntity entity = new AuditLogEntity();

        // ① 注解元数据
        entity.setModule(auditLog.module());
        entity.setOperation(auditLog.operation());
        entity.setDescription(auditLog.description());

        // ② 方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        entity.setMethod(className + "." + methodName);

        // ③ 当前用户信息（从 Spring Security 上下文获取）
        extractCurrentUser(entity);

        // ④ HTTP 请求信息
        extractRequestInfo(entity, joinPoint, auditLog, signature);

        return entity;
    }

    /**
     * 提取当前登录用户信息
     */
    private void extractCurrentUser(AuditLogEntity entity) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof Long userId) {
                entity.setUserId(userId);
                // username 从 Authentication 的 name 获取（如果有的话）
                entity.setUsername(authentication.getName());
            }
        } catch (Exception e) {
            log.debug("获取当前用户信息失败（可能是匿名接口）: {}", e.getMessage());
        }
    }

    /**
     * 提取 HTTP 请求信息
     */
    private void extractRequestInfo(AuditLogEntity entity, ProceedingJoinPoint joinPoint,
                                     AuditLog auditLog, MethodSignature signature) {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }

            HttpServletRequest request = attributes.getRequest();
            entity.setRequestUrl(request.getRequestURI());
            entity.setRequestMethod(request.getMethod());
            entity.setRequestIp(getClientIp(request));
            entity.setUserAgent(truncate(request.getHeader("User-Agent"), 500));

            // ⑤ 请求参数（根据注解配置决定是否记录）
            if (auditLog.saveParams()) {
                String params = extractParams(joinPoint, signature);
                entity.setRequestParams(truncate(params, MAX_PARAMS_LENGTH));
            }
        } catch (Exception e) {
            log.debug("提取请求信息失败: {}", e.getMessage());
        }
    }

    /**
     * 提取方法参数为 JSON 字符串
     * <p>
     * 自动过滤 Authentication、MultipartFile 等不可序列化参数，
     * 并对敏感字段脱敏处理。
     */
    private String extractParams(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        try {
            String[] paramNames = signature.getParameterNames();
            Object[] paramValues = joinPoint.getArgs();
            if (paramNames == null || paramValues == null) {
                return null;
            }

            Map<String, Object> paramsMap = new LinkedHashMap<>();
            for (int i = 0; i < paramNames.length; i++) {
                String name = paramNames[i];
                Object value = paramValues[i];

                // 跳过不可序列化的参数类型
                if (value instanceof Authentication
                        || value instanceof HttpServletRequest
                        || value instanceof MultipartFile) {
                    if (value instanceof MultipartFile file) {
                        paramsMap.put(name, "FILE:" + file.getOriginalFilename()
                                + "(" + file.getSize() + " bytes)");
                    }
                    continue;
                }

                // 敏感字段脱敏
                if (isSensitiveField(name)) {
                    paramsMap.put(name, "******");
                } else {
                    paramsMap.put(name, value);
                }
            }

            return objectMapper.writeValueAsString(paramsMap);
        } catch (Exception e) {
            log.debug("序列化请求参数失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否为敏感字段
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        for (String sensitive : SENSITIVE_FIELDS) {
            if (lower.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取客户端真实 IP（兼容反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能包含多个 IP，取第一个
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * 字符串截断
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
