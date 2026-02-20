-- 简历表
USE smartats;

DROP TABLE IF EXISTS `resumes`;

CREATE TABLE `resumes` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '简历ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
    `file_path` VARCHAR(500) NOT NULL COMMENT '文件路径（MinIO objectName）',
    `file_url` VARCHAR(500) NOT NULL COMMENT '文件访问URL',
    `file_size` BIGINT NOT NULL COMMENT '文件大小（字节）',
    `file_hash` VARCHAR(32) NOT NULL UNIQUE COMMENT '文件MD5哈希（去重）',
    `file_type` VARCHAR(50) NOT NULL COMMENT '文件类型（application/pdf等）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PARSING' COMMENT '解析状态：PARSING、COMPLETED、FAILED',
    `error_message` TEXT COMMENT '错误信息（失败时记录）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_file_hash` (`file_hash`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历表';

-- 验证表是否创建成功
SELECT 'Table resumes created successfully' AS result;
