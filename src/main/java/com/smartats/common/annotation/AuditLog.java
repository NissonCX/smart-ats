package com.smartats.common.annotation;

import java.lang.annotation.*;

/**
 * 审计日志注解
 * <p>
 * 标记在 Controller / Service 方法上，由 AOP 切面自动记录操作日志。
 * <p>
 * 使用示例：
 * <pre>
 * {@code @AuditLog(module = "职位管理", operation = "CREATE", description = "创建职位")}
 * public Result<Long> createJob(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 业务模块名称
     * <p>
     * 例如："职位管理"、"简历管理"、"候选人管理"
     */
    String module() default "";

    /**
     * 操作类型
     * <p>
     * 建议使用标准动词：CREATE / UPDATE / DELETE / QUERY / UPLOAD / EXPORT / LOGIN / LOGOUT
     */
    String operation() default "";

    /**
     * 操作描述（可选，更具体的业务说明）
     */
    String description() default "";

    /**
     * 是否记录请求参数（默认 true）
     * <p>
     * 对于包含敏感信息的接口（如登录），建议设为 false
     */
    boolean saveParams() default true;

    /**
     * 是否记录响应结果（默认 false，避免日志过大）
     */
    boolean saveResult() default false;
}
