package com.smartats.module.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.URL;
import lombok.Data;

import java.util.List;

/**
 * 创建 Webhook 配置请求
 */
@Data
public class WebhookCreateRequest {

    @NotBlank(message = "Webhook URL 不能为空")
    @URL(message = "Webhook URL 格式不正确")
    private String url;

    @NotNull(message = "事件列表不能为空")
    private List<String> events;

    private String description;
}
