package com.smartats.module.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Webhook 事件负载
 * 发送到用户配置的回调 URL 的数据格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookPayload {

    /**
     * 事件 ID（唯一标识）
     */
    private String eventId;

    /**
     * 事件类型（如：resume.uploaded, resume.parse_completed）
     */
    private String eventType;

    /**
     * 事件发生时间
     */
    private LocalDateTime timestamp;

    /**
     * 事件数据（具体内容根据事件类型不同而不同）
     */
    private Map<String, Object> data;

    /**
     * 签名（用于验证请求真实性）
     */
    private String signature;

    /**
     * Webhook 版本
     */
    private String version = "1.0";
}
