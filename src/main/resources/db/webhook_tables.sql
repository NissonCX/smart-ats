-- ================================================================
-- Webhook 功能相关数据库表
-- ================================================================

-- Webhook 配置表
CREATE TABLE IF NOT EXISTS `webhook_configs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `url` VARCHAR(500) NOT NULL COMMENT 'Webhook 回调 URL',
    `events` VARCHAR(500) NOT NULL COMMENT '订阅的事件类型（逗号分隔）',
    `secret` VARCHAR(100) NOT NULL COMMENT '签名密钥',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述/备注',
    `failure_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    `last_failure_at` DATETIME DEFAULT NULL COMMENT '最后一次失败时间',
    `last_success_at` DATETIME DEFAULT NULL COMMENT '最后一次成功时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook 配置表';

-- Webhook 调用日志表
CREATE TABLE IF NOT EXISTS `webhook_logs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    `webhook_id` BIGINT UNSIGNED NOT NULL COMMENT 'Webhook 配置 ID',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
    `payload` TEXT COMMENT '请求负载（JSON）',
    `response_status` INT UNSIGNED DEFAULT NULL COMMENT 'HTTP 响应状态码',
    `response_body` TEXT COMMENT 'HTTP 响应内容',
    `error_message` TEXT COMMENT '错误信息',
    `status` VARCHAR(20) NOT NULL COMMENT '调用状态：SUCCESS, FAILED, RETRYING',
    `retry_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
    `duration` BIGINT UNSIGNED DEFAULT NULL COMMENT '调用耗时（毫秒）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX `idx_webhook_id` (`webhook_id`),
    INDEX `idx_event_type` (`event_type`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook 调用日志表';
