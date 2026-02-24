package com.smartats.module.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 * <p>
 * 记录系统中所有关键业务操作，用于安全审计、问题追踪和合规性要求。
 */
@Data
@TableName("audit_logs")
public class AuditLogEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人用户 ID（未登录则为 null） */
    private Long userId;

    /** 操作人用户名 */
    private String username;

    /** 业务模块（如：职位管理、简历管理） */
    private String module;

    /** 操作类型（如：CREATE、UPDATE、DELETE） */
    private String operation;

    /** 操作描述 */
    private String description;

    /** Java 方法签名（类名.方法名） */
    private String method;

    /** 请求 URL */
    private String requestUrl;

    /** HTTP 请求方法（GET / POST / PUT / DELETE） */
    private String requestMethod;

    /** 请求参数（JSON 格式，脱敏后存储） */
    private String requestParams;

    /** 请求方 IP 地址 */
    private String requestIp;

    /** 用户代理（User-Agent） */
    private String userAgent;

    /** 操作结果状态：SUCCESS / FAILED */
    private String status;

    /** 失败时的错误消息 */
    private String errorMessage;

    /** 方法执行耗时（毫秒） */
    private Long duration;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
