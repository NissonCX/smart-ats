-- SmartATS 数据库初始化脚本
-- 创建时间: 2026-02-14

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `email` VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    `role` ENUM('ADMIN', 'HR', 'INTERVIEWER') NOT NULL DEFAULT 'HR' COMMENT '角色',
    `daily_ai_quota` INT NOT NULL DEFAULT 100 COMMENT '每日AI调用配额',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_email` (`email`),
    INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 职位表
CREATE TABLE IF NOT EXISTS `jobs` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '职位ID',
    `title` VARCHAR(100) NOT NULL COMMENT '职位标题',
    `department` VARCHAR(50) COMMENT '部门',
    `description` TEXT NOT NULL COMMENT '职位描述',
    `requirements` TEXT NOT NULL COMMENT '任职要求',
    `required_skills` JSON COMMENT '必需技能标签 ["Java", "Spring"]',
    `salary_min` INT COMMENT '薪资下限(K)',
    `salary_max` INT COMMENT '薪资上限(K)',
    `experience_min` INT DEFAULT 0 COMMENT '最低经验年限',
    `experience_max` INT COMMENT '最高经验年限',
    `education` ENUM('不限', '大专', '本科', '硕士', '博士') DEFAULT '不限' COMMENT '学历要求',
    `job_type` ENUM('FULL_TIME', 'PART_TIME', 'INTERN') DEFAULT 'FULL_TIME' COMMENT '工作类型',
    `status` ENUM('DRAFT', 'PUBLISHED', 'CLOSED') DEFAULT 'DRAFT' COMMENT '状态',
    `creator_id` BIGINT NOT NULL COMMENT '创建者ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_status` (`status`),
    INDEX `idx_creator` (`creator_id`),
    FULLTEXT INDEX `ft_content` (`title`, `description`, `requirements`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位表';

-- 3. 简历文件表
CREATE TABLE IF NOT EXISTS `resumes` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '简历ID',
    `uploader_id` BIGINT NOT NULL COMMENT '上传者ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(1024) NOT NULL COMMENT '存储路径',
    `file_hash` VARCHAR(64) NOT NULL COMMENT 'MD5哈希值(去重用)',
    `file_size` BIGINT NOT NULL COMMENT '文件大小(bytes)',
    `file_type` VARCHAR(20) NOT NULL COMMENT '文件类型 pdf/docx/doc',
    `status` ENUM('PENDING', 'QUEUED', 'PROCESSING', 'SUCCESS', 'FAILED') DEFAULT 'PENDING' COMMENT '解析状态',
    `error_message` VARCHAR(500) COMMENT '解析失败原因',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `processed_at` DATETIME COMMENT '解析完成时间',
    UNIQUE INDEX `uk_file_hash` (`file_hash`),
    INDEX `idx_uploader` (`uploader_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历文件表';

-- 4. 候选人表 (AI提取的结构化数据)
CREATE TABLE IF NOT EXISTS `candidates` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '候选人ID',
    `resume_id` BIGINT NOT NULL UNIQUE COMMENT '关联简历ID',
    `name` VARCHAR(50) COMMENT '姓名',
    `phone` VARCHAR(20) COMMENT '手机号',
    `email` VARCHAR(100) COMMENT '邮箱',
    `gender` ENUM('MALE', 'FEMALE', 'UNKNOWN') DEFAULT 'UNKNOWN' COMMENT '性别',
    `birth_year` INT COMMENT '出生年份（对应 Candidate.age）',
    `experience_years` INT COMMENT '工作年限（对应 Candidate.workYears）',
    `highest_education` VARCHAR(20) COMMENT '最高学历（对应 Candidate.education）',
    `graduate_school` VARCHAR(100) COMMENT '毕业院校（对应 Candidate.school）',
    `major` VARCHAR(100) COMMENT '专业',
    `graduation_year` INT COMMENT '毕业年份（对应 Candidate.graduationYear）',
    `current_company` VARCHAR(100) COMMENT '当前公司',
    `current_position` VARCHAR(100) COMMENT '当前职位',
    `skills` JSON COMMENT '技能标签 ["Java", "Spring Boot"]',
    `work_experiences` JSON COMMENT '工作经历（对应 Candidate.workExperience）',
    `self_evaluation` TEXT COMMENT '自我评价（对应 Candidate.selfEvaluation）',
    `project_experience` JSON COMMENT '项目经历（对应 Candidate.projectExperience）',
    `education_history` JSON COMMENT '教育经历（保留，未映射到实体）',
    `raw_extracted_json` JSON COMMENT 'AI原始提取结果（对应 Candidate.rawJson）',
    `confidence_score` DOUBLE COMMENT 'AI解析置信度（对应 Candidate.confidenceScore）',
    `parsed_at` DATETIME COMMENT 'AI解析时间（对应 Candidate.parsedAt）',
    `vector_id` VARCHAR(100) COMMENT '向量数据库ID',
    `ai_summary` TEXT COMMENT 'AI生成的候选人摘要',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_resume` (`resume_id`),
    INDEX `idx_name` (`name`),
    INDEX `idx_experience` (`experience_years`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候选人表';

-- 5. 职位申请表
CREATE TABLE IF NOT EXISTS `job_applications` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申请ID',
    `job_id` BIGINT NOT NULL COMMENT '职位ID',
    `candidate_id` BIGINT NOT NULL COMMENT '候选人ID',
    `match_score` DECIMAL(5,2) COMMENT 'AI匹配度 0-100',
    `match_reasons` JSON COMMENT '匹配原因分析',
    `match_calculated_at` DATETIME COMMENT '匹配度计算时间',
    `status` ENUM('PENDING', 'SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN') DEFAULT 'PENDING' COMMENT '状态',
    `hr_notes` TEXT COMMENT 'HR备注',
    `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_job_candidate` (`job_id`, `candidate_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_score` (`match_score` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位申请表';

-- 6. 面试记录表
CREATE TABLE IF NOT EXISTS `interview_records` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '面试记录ID',
    `application_id` BIGINT NOT NULL COMMENT '申请ID',
    `interviewer_id` BIGINT NOT NULL COMMENT '面试官ID',
    `round` INT NOT NULL DEFAULT 1 COMMENT '第几轮面试',
    `interview_type` ENUM('PHONE', 'VIDEO', 'ONSITE', 'WRITTEN_TEST') DEFAULT 'VIDEO' COMMENT '面试类型',
    `scheduled_at` DATETIME NOT NULL COMMENT '面试时间',
    `duration_minutes` INT DEFAULT 60 COMMENT '预计时长',
    `status` ENUM('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW') DEFAULT 'SCHEDULED' COMMENT '状态',
    `feedback` TEXT COMMENT '面试反馈',
    `score` INT COMMENT '评分 1-10',
    `recommendation` ENUM('STRONG_YES', 'YES', 'NEUTRAL', 'NO', 'STRONG_NO') COMMENT '推荐等级',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_application` (`application_id`),
    INDEX `idx_interviewer` (`interviewer_id`),
    INDEX `idx_scheduled` (`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试记录表';

-- 插入测试用户
INSERT INTO `users` (`username`, `password`, `email`, `role`, `daily_ai_quota`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'admin@smartats.com', 'ADMIN', 1000),
('hr01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'hr01@smartats.com', 'HR', 100)
ON DUPLICATE KEY UPDATE `email` = VALUES(`email`);

-- 注意：上面的密码是 'password123' 的 BCrypt 加密结果
