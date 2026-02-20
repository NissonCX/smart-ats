package com.smartats.module.webhook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Webhook 配置实体
 * 存储用户配置的 Webhook URL 和订阅的事件类型
 */
@Data
@TableName("webhook_configs")
public class WebhookConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * Webhook URL（接收事件的回调地址）
     */
    private String url;

    /**
     * 订阅的事件类型（JSON 数组，如：["resume.uploaded", "resume.parse_completed"]）
     */
    @TableField(value = "events")
    private String events;

    /**
     * 签名密钥（HMAC-SHA256）
     */
    private String secret;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 描述/备注
     */
    private String description;

    /**
     * 失败次数（连续失败达到阈值后自动禁用）
     */
    private Integer failureCount;

    /**
     * 最后一次失败时间
     */
    private LocalDateTime lastFailureAt;

    /**
     * 最后一次成功时间
     */
    private LocalDateTime lastSuccessAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
