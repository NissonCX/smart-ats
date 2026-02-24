-- ============================================================================
-- 审计日志表 (audit_logs)
-- 创建时间: 2026-02-25
-- 说明: 记录系统中所有关键业务操作，用于安全审计、问题追踪和合规性要求
-- ============================================================================

CREATE TABLE IF NOT EXISTS `audit_logs` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id`        BIGINT       NULL     COMMENT '操作人用户 ID（未登录则为 NULL）',
    `username`       VARCHAR(64)  NULL     COMMENT '操作人用户名',
    `module`         VARCHAR(32)  NOT NULL COMMENT '业务模块（JOB/RESUME/CANDIDATE/APPLICATION/INTERVIEW/WEBHOOK/AUTH/ANALYTICS）',
    `operation`      VARCHAR(32)  NOT NULL COMMENT '操作类型（CREATE/UPDATE/DELETE/LOGIN/UPLOAD 等）',
    `description`    VARCHAR(255) NULL     COMMENT '操作描述',
    `method`         VARCHAR(255) NOT NULL COMMENT 'Java 方法签名（类名.方法名）',
    `request_url`    VARCHAR(512) NOT NULL COMMENT '请求 URL',
    `request_method` VARCHAR(10)  NOT NULL COMMENT 'HTTP 方法（GET/POST/PUT/DELETE）',
    `request_params` TEXT         NULL     COMMENT '请求参数（JSON 格式，脱敏后存储，最大 2000 字符）',
    `request_ip`     VARCHAR(45)  NOT NULL COMMENT '请求方 IP 地址（支持 IPv6）',
    `user_agent`     VARCHAR(512) NULL     COMMENT '客户端 User-Agent',
    `status`         VARCHAR(10)  NOT NULL COMMENT '操作结果（SUCCESS / FAILED）',
    `error_message`  TEXT         NULL     COMMENT '失败时的错误消息',
    `duration`       BIGINT       NOT NULL COMMENT '方法执行耗时（毫秒）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),

    -- 按操作人查询
    INDEX `idx_user_id` (`user_id`),

    -- 按模块+操作类型查询
    INDEX `idx_module_operation` (`module`, `operation`),

    -- 按时间范围查询（审计报表高频场景）
    INDEX `idx_created_at` (`created_at`),

    -- 按状态过滤失败操作
    INDEX `idx_status` (`status`),

    -- 复合索引：按用户+时间范围查询
    INDEX `idx_user_time` (`user_id`, `created_at`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '审计日志表';
