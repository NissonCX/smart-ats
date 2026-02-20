package com.smartats.module.webhook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Webhook 调用日志
 * 记录每次 Webhook 调用的详细信息，用于调试和重试
 */
@Data
@TableName("webhook_logs")
public class WebhookLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Webhook 配置 ID
     */
    private Long webhookId;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 请求负载（JSON）
     */
    private String payload;

    /**
     * HTTP 响应状态码
     */
    private Integer responseStatus;

    /**
     * HTTP 响应内容
     */
    private String responseBody;

    /**
     * 错误信息（如果发送失败）
     */
    private String errorMessage;

    /**
     * 调用状态：SUCCESS, FAILED, RETRYING
     */
    private String status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 调用耗时（毫秒）
     */
    private Long duration;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
