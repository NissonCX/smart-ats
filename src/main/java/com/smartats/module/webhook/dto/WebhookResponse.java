package com.smartats.module.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Webhook 配置响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {

    private Long id;
    private String url;
    private List<String> events;
    private String description;
    private Boolean enabled;
    private Integer failureCount;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailureAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 密钥提示（只显示前 4 位和后 4 位）
     */
    private String secretHint;
}
