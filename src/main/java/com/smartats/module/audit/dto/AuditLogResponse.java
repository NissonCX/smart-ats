package com.smartats.module.audit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志响应 DTO
 */
@Data
public class AuditLogResponse {

    private Long id;

    private Long userId;

    private String username;

    /** 业务模块 */
    private String module;

    /** 操作类型 */
    private String operation;

    /** 操作描述 */
    private String description;

    /** 方法签名 */
    private String method;

    /** 请求 URL */
    private String requestUrl;

    /** HTTP 方法 */
    private String requestMethod;

    /** 请求参数 */
    private String requestParams;

    /** 请求 IP */
    private String requestIp;

    /** 操作结果：SUCCESS / FAILED */
    private String status;

    /** 错误消息 */
    private String errorMessage;

    /** 执行耗时（ms） */
    private Long duration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
