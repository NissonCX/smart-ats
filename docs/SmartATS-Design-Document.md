# SmartATS - 智能招聘与简历分析系统

## 📌 项目概述

**SmartATS** 是一个面向 HR 的智能招聘管理系统。系统支持简历批量上传、AI 自动解析提取结构化信息、RAG 语义人才搜索等核心功能。

### 技术栈

| 组件 | 技术选型 | 用途 |
|------|----------|------|
| 核心框架 | Spring Boot 3.x | 基础框架 |
| ORM | MyBatis-Plus | 数据库操作 |
| 数据库 | MySQL 8.0 | 业务数据存储 |
| 缓存/限流/锁 | Redis + Redisson | 高性能缓存、分布式锁、限流 |
| 消息队列 | RabbitMQ | 异步任务解耦 |
| AI 集成 | Spring AI | LLM 调用、Embedding、RAG |
| 向量数据库 | Milvus / PgVector | 简历向量存储与检索 |

---

## 🏗️ 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SmartATS Architecture                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────┐     ┌─────────────────────────────────────────────────────┐  │
│   │   HR    │────▶│                   API Gateway                       │  │
│   │  Client │     │  (Rate Limiting via Redis + Token Bucket/Lua)       │  │
│   └─────────┘     └─────────────────────────────────────────────────────┘  │
│                                      │                                      │
│                                      ▼                                      │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                        Spring Boot Application                       │  │
│   │  ┌───────────────┐  ┌───────────────┐  ┌───────────────────────┐   │  │
│   │  │ Resume Module │  │  Job Module   │  │   Search Module       │   │  │
│   │  │  - Upload     │  │  - CRUD       │  │   - AI Semantic Search│   │  │
│   │  │  - Status     │  │  - Cache      │  │   - Hybrid Query      │   │  │
│   │  └───────┬───────┘  └───────────────┘  └───────────────────────┘   │  │
│   │          │                                         │                │  │
│   └──────────┼─────────────────────────────────────────┼────────────────┘  │
│              │                                         │                    │
│              ▼                                         ▼                    │
│   ┌──────────────────┐                    ┌──────────────────────────┐     │
│   │    RabbitMQ      │                    │      Spring AI           │     │
│   │  ┌────────────┐  │                    │  ┌────────────────────┐  │     │
│   │  │ resume.queue│ │                    │  │ Embedding Model    │  │     │
│   │  └─────┬──────┘  │                    │  │ (text-embedding)   │  │     │
│   │        │         │                    │  ├────────────────────┤  │     │
│   │  ┌─────▼──────┐  │                    │  │ Chat Model (GPT/   │  │     │
│   │  │ DLX Queue  │  │                    │  │ DeepSeek/Ollama)   │  │     │
│   │  │ (dead letter)│ │                    │  └────────────────────┘  │     │
│   │  └────────────┘  │                    └──────────────────────────┘     │
│   └──────────────────┘                                 │                    │
│              │                                         │                    │
│              ▼                                         ▼                    │
│   ┌──────────────────────────────────────────────────────────────────┐     │
│   │                        Resume Parser Consumer                     │     │
│   │   1. Acquire Redisson Lock (by file MD5)                         │     │
│   │   2. Call Spring AI for extraction                               │     │
│   │   3. Store structured data to MySQL                              │     │
│   │   4. Store embedding to Vector DB                                │     │
│   │   5. Update Redis task status                                    │     │
│   └──────────────────────────────────────────────────────────────────┘     │
│                                                                             │
│   ┌───────────────┐   ┌───────────────┐   ┌───────────────────────────┐   │
│   │    MySQL      │   │    Redis      │   │    Milvus/PgVector        │   │
│   │  - jobs       │   │  - task:*     │   │  - resume_vectors         │   │
│   │  - candidates │   │  - rate:*     │   │    (id, embedding,        │   │
│   │  - resumes    │   │  - lock:*     │   │     metadata)             │   │
│   │  - users      │   │  - cache:*    │   │                           │   │
│   └───────────────┘   └───────────────┘   └───────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📊 数据库设计 (MySQL)

### ER 关系图

```
┌──────────────┐       ┌──────────────┐       ┌──────────────────┐
│    users     │       │    jobs      │       │   candidates     │
├──────────────┤       ├──────────────┤       ├──────────────────┤
│ id (PK)      │       │ id (PK)      │       │ id (PK)          │
│ username     │──┐    │ title        │    ┌──│ resume_id (FK)   │
│ password     │  │    │ description  │    │  │ name             │
│ email        │  │    │ requirements │    │  │ phone            │
│ role         │  │    │ salary_range │    │  │ email            │
│ daily_quota  │  │    │ status       │    │  │ skills (JSON)    │
│ created_at   │  │    │ creator_id(FK)│◀──┘  │ experience_years │
└──────────────┘  │    │ created_at   │       │ education        │
                  │    └──────────────┘       │ extracted_data   │
                  │                           │ created_at       │
                  │    ┌───────────────┐       └──────────────────┘
                  │    │   resumes    │              ▲
                  │    ├──────────────┤              │
                  └───▶│ id (PK)      │──────────────┘
                       │ uploader_id  │
                       │ file_path    │
                       │ file_hash    │ (MD5, 用于去重)
                       │ file_name    │
                       │ file_size    │
                       │ status       │ (PENDING/PROCESSING/SUCCESS/FAILED)
                       │ error_msg    │
                       │ created_at   │
                       └──────────────┘

┌──────────────────────┐       ┌──────────────────────┐
│  interview_records   │       │   job_applications   │
├──────────────────────┤       ├──────────────────────┤
│ id (PK)              │       │ id (PK)              │
│ candidate_id (FK)    │       │ job_id (FK)          │
│ job_id (FK)          │       │ candidate_id (FK)    │
│ interviewer_id (FK)  │       │ status               │
│ round                │       │ match_score          │ (AI 计算的匹配度)
│ feedback             │       │ applied_at           │
│ score                │       └──────────────────────┘
│ status               │
│ scheduled_at         │
└──────────────────────┘
```

### 表结构详细定义

#### 1. users - 用户表
```sql
CREATE TABLE `users` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `role` ENUM('ADMIN', 'HR', 'INTERVIEWER') NOT NULL DEFAULT 'HR',
    `daily_ai_quota` INT NOT NULL DEFAULT 100 COMMENT '每日AI调用配额',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_email` (`email`),
    INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 2. jobs - 职位表
```sql
CREATE TABLE `jobs` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `title` VARCHAR(100) NOT NULL,
    `department` VARCHAR(50),
    `description` TEXT NOT NULL COMMENT '职位描述',
    `requirements` TEXT NOT NULL COMMENT '任职要求',
    `required_skills` JSON COMMENT '必需技能标签 ["Java", "Spring"]',
    `salary_min` INT COMMENT '薪资下限(K)',
    `salary_max` INT COMMENT '薪资上限(K)',
    `experience_min` INT DEFAULT 0 COMMENT '最低经验年限',
    `experience_max` INT COMMENT '最高经验年限',
    `education` ENUM('不限', '大专', '本科', '硕士', '博士') DEFAULT '不限',
    `job_type` ENUM('FULL_TIME', 'PART_TIME', 'INTERN') DEFAULT 'FULL_TIME',
    `status` ENUM('DRAFT', 'PUBLISHED', 'CLOSED') DEFAULT 'DRAFT',
    `creator_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`),
    INDEX `idx_creator` (`creator_id`),
    FULLTEXT INDEX `ft_content` (`title`, `description`, `requirements`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 3. resumes - 简历文件表
```sql
CREATE TABLE `resumes` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `uploader_id` BIGINT NOT NULL COMMENT '上传者ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(1024) NOT NULL COMMENT '存储路径',
    `file_hash` VARCHAR(64) NOT NULL COMMENT 'MD5哈希值，用于去重',
    `file_size` BIGINT NOT NULL COMMENT '文件大小(bytes)',
    `file_type` VARCHAR(20) NOT NULL COMMENT 'pdf/docx/doc',
    `status` ENUM('PENDING', 'QUEUED', 'PROCESSING', 'SUCCESS', 'FAILED') DEFAULT 'PENDING',
    `error_message` VARCHAR(500) COMMENT '解析失败原因',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `processed_at` DATETIME COMMENT '解析完成时间',
    UNIQUE INDEX `uk_file_hash` (`file_hash`),
    INDEX `idx_uploader` (`uploader_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 4. candidates - 候选人表 (AI提取的结构化数据)
```sql
CREATE TABLE `candidates` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `resume_id` BIGINT NOT NULL UNIQUE COMMENT '关联简历ID',
    `name` VARCHAR(50) COMMENT '姓名',
    `phone` VARCHAR(20) COMMENT '手机号',
    `email` VARCHAR(100) COMMENT '邮箱',
    `gender` ENUM('MALE', 'FEMALE', 'UNKNOWN') DEFAULT 'UNKNOWN',
    `birth_year` INT COMMENT '出生年份',
    `experience_years` INT COMMENT '工作年限',
    `highest_education` VARCHAR(20) COMMENT '最高学历',
    `graduate_school` VARCHAR(100) COMMENT '毕业院校',
    `major` VARCHAR(100) COMMENT '专业',
    `current_company` VARCHAR(100) COMMENT '当前公司',
    `current_position` VARCHAR(100) COMMENT '当前职位',
    `skills` JSON COMMENT '技能标签 ["Java", "Spring Boot", "Redis"]',
    `work_experiences` JSON COMMENT '工作经历 [{company, position, duration, description}]',
    `education_history` JSON COMMENT '教育经历',
    `raw_extracted_json` JSON COMMENT 'AI原始提取结果',
    `vector_id` VARCHAR(100) COMMENT '向量数据库中的ID',
    `ai_summary` TEXT COMMENT 'AI生成的候选人摘要',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_resume` (`resume_id`),
    INDEX `idx_name` (`name`),
    INDEX `idx_experience` (`experience_years`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 5. job_applications - 职位申请表
```sql
CREATE TABLE `job_applications` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `job_id` BIGINT NOT NULL,
    `candidate_id` BIGINT NOT NULL,
    `match_score` DECIMAL(5,2) COMMENT 'AI计算的匹配度 0-100',
    `match_reasons` JSON COMMENT '匹配原因分析',
    `match_calculated_at` DATETIME COMMENT '匹配度计算时间',
    `status` ENUM('PENDING', 'SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN') DEFAULT 'PENDING',
    `hr_notes` TEXT COMMENT 'HR备注',
    `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_job_candidate` (`job_id`, `candidate_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_score` (`match_score` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 6. interview_records - 面试记录表
```sql
CREATE TABLE `interview_records` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `application_id` BIGINT NOT NULL COMMENT '关联申请ID',
    `interviewer_id` BIGINT NOT NULL COMMENT '面试官ID',
    `round` INT NOT NULL DEFAULT 1 COMMENT '第几轮面试',
    `interview_type` ENUM('PHONE', 'VIDEO', 'ONSITE', 'WRITTEN_TEST') DEFAULT 'VIDEO',
    `scheduled_at` DATETIME NOT NULL COMMENT '面试时间',
    `duration_minutes` INT DEFAULT 60 COMMENT '预计时长',
    `status` ENUM('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW') DEFAULT 'SCHEDULED',
    `feedback` TEXT COMMENT '面试反馈',
    `score` INT COMMENT '评分 1-10',
    `recommendation` ENUM('STRONG_YES', 'YES', 'NEUTRAL', 'NO', 'STRONG_NO'),
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_application` (`application_id`),
    INDEX `idx_interviewer` (`interviewer_id`),
    INDEX `idx_scheduled` (`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 🔴 Redis 数据结构设计

### Key 命名规范

| Key Pattern | Type | 用途 | TTL |
|-------------|------|------|-----|
| `task:resume:{taskId}` | Hash | 简历解析任务状态 | 24h |
| `rate:ai:{userId}:{date}` | String (Counter) | AI调用次数限流 | 24h |
| `rate:upload:{userId}` | String | 上传频率限流 | 1min |
| `lock:resume:{fileHash}` | String | 简历解析分布式锁 | 10min |
| `lock:application:{jobId}:{candidateId}` | String | 防止重复投递锁 | 5min |
| `cache:job:{jobId}` | String (JSON) | 职位信息缓存 | 30min |
| `cache:job:hot` | ZSet | 热门职位排行 | 10min |
| `cache:candidate:{id}` | String (JSON) | 候选人信息缓存 | 30min |
| `dedup:resume:{fileHash}` | String | 文件去重标记 | 7d |

### 详细结构说明

#### 1. 任务状态追踪
```
Key: task:resume:{taskId}
Type: Hash
Fields:
  - status: QUEUED | PROCESSING | SUCCESS | FAILED
  - progress: 0-100 (解析进度百分比)
  - resumeId: 关联的简历ID
  - startTime: 开始处理时间戳
  - message: 状态描述或错误信息
TTL: 86400 (24小时)
```

#### 2. AI限流计数器 (滑动窗口)
```
Key: rate:ai:{userId}:{yyyyMMdd}
Type: String (Integer)
Value: 当日已调用次数
TTL: 86400 (24小时后自动过期)

# 额外的分钟级限流
Key: rate:ai:minute:{userId}:{yyyyMMddHHmm}
Type: String (Integer)
Value: 当前分钟调用次数
TTL: 60
```

#### 3. 分布式锁
```
Key: lock:resume:{fileHash}
Type: String
Value: {uuid}:{threadId}
TTL: 30s (默认，由Redisson Watchdog自动续期)
说明: 必须使用Redisson的Watchdog机制，防止解析时间过长导致锁提前释放。
```

---

## 🐰 RabbitMQ 设计

### Exchange 与 Queue 拓扑

```
┌─────────────────────────────────────────────────────────────────┐
│                     RabbitMQ Topology                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Producer                                                      │
│      │                                                          │
│      ▼                                                          │
│   ┌──────────────────────┐                                      │
│   │  smartats.exchange   │  (Direct Exchange)                   │
│   └──────────┬───────────┘                                      │
│              │                                                  │
│              │ routing_key: resume.parse                        │
│              ▼                                                  │
│   ┌──────────────────────┐                                      │
│   │ resume.parse.queue   │◀─────────┐                           │
│   │ (主队列)              │          │ x-dead-letter-exchange   │
│   │ TTL: 30min           │          │ x-dead-letter-routing-key │
│   └──────────┬───────────┘          │                           │
│              │                      │                           │
│              │ Consumer             │                           │
│              ▼                      │                           │
│   ┌──────────────────────┐          │                           │
│   │  Processing Failed   │──────────┘                           │
│   │  (Nack + Requeue=F)  │                                      │
│   └──────────────────────┘                                      │
│              │                                                  │
│              ▼                                                  │
│   ┌──────────────────────┐                                      │
│   │   smartats.dlx       │  (Dead Letter Exchange)              │
│   └──────────┬───────────┘                                      │
│              │                                                  │
│              │ routing_key: resume.parse.failed                 │
│              ▼                                                  │
│   ┌──────────────────────┐                                      │
│   │ resume.parse.dlq     │  (Dead Letter Queue)                 │
│   │ (死信队列，存放失败任务) │                                     │
│   └──────────────────────┘                                      │
│              │                                                  │
│              ▼                                                  │
│   ┌──────────────────────┐                                      │
│   │  DLQ Consumer        │  (定时任务扫描，人工介入或重试)          │
│   └──────────────────────┘                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 消息体定义

```json
// ResumeParseMessage
{
  "taskId": "uuid-xxxx-xxxx",
  "resumeId": 12345,
  "filePath": "/data/resumes/2024/01/xxx.pdf",
  "fileHash": "md5-hash-value",
  "uploaderId": 100,
  "timestamp": 1704067200000,
  "retryCount": 0
}
```

### Queue 配置参数

| 参数 | 值 | 说明 |
|------|-----|------|
| x-message-ttl | 1800000 | 消息30分钟未消费则进入死信 |
| x-dead-letter-exchange | smartats.dlx | 死信交换机 |
| x-dead-letter-routing-key | resume.parse.failed | 死信路由键 |
| x-max-length | 10000 | 队列最大长度 |
| durable | true | 持久化 |

---

## 🤖 Spring AI 集成设计

### Prompt 模板

#### 简历信息提取 Prompt

```text
# System Prompt
你是一个专业的简历解析助手。你的任务是从简历文本中提取结构化信息。
请严格按照指定的JSON格式输出，不要添加任何额外的解释或markdown标记。

## 输出格式要求
{
  "name": "姓名",
  "phone": "手机号",
  "email": "邮箱",
  "gender": "MALE/FEMALE/UNKNOWN",
  "birthYear": 1990,
  "experienceYears": 5,
  "highestEducation": "本科/硕士/博士",
  "graduateSchool": "毕业院校",
  "major": "专业",
  "currentCompany": "当前公司",
  "currentPosition": "当前职位",
  "skills": ["Java", "Spring Boot", "MySQL"],
  "workExperiences": [
    {
      "company": "公司名",
      "position": "职位",
      "startDate": "2020-01",
      "endDate": "2023-06",
      "description": "工作描述"
    }
  ],
  "educationHistory": [
    {
      "school": "学校名",
      "degree": "学位",
      "major": "专业",
      "startDate": "2015-09",
      "endDate": "2019-06"
    }
  ],
  "summary": "一句话总结该候选人的核心竞争力"
}

## 注意事项
1. 如果某字段无法从简历中提取，设为null
2. skills数组请提取所有技术技能、工具、编程语言
3. experienceYears请根据工作经历计算，精确到整数
4. 日期格式统一为 YYYY-MM

# User Prompt
请解析以下简历内容：

---
{resumeContent}
---
```

#### RAG 搜索 Prompt

```text
# System Prompt
你是一个智能人才搜索助手。你将收到用户的搜索查询和一些相关的候选人简历片段。
请根据这些信息，分析每位候选人与查询的匹配程度。

## 你的任务
1. 理解用户的招聘需求
2. 分析每位候选人的匹配度
3. 按匹配度从高到低排序
4. 给出匹配理由

## 输出格式
{
  "analysis": "对用户需求的理解",
  "candidates": [
    {
      "candidateId": 123,
      "matchScore": 85,
      "matchReasons": ["3年Java经验", "熟悉Spring Boot", "有大厂背景"],
      "concerns": ["缺乏Redis经验"]
    }
  ]
}

# User Prompt
## 招聘需求
{userQuery}

## 候选人简历
{retrievedResumes}
```

---

## 📡 API 接口文档

### 基础规范

- **Base URL:** `/api/v1`
- **认证方式:** JWT Bearer Token
- **统一响应格式:**

```json
{
  "code": 200,
  "message": "success",
  "data": { },
  "timestamp": 1704067200000
}
```

- **错误响应格式:**

```json
{
  "code": 40001,
  "message": "参数校验失败",
  "errors": [
    { "field": "email", "message": "邮箱格式不正确" }
  ],
  "timestamp": 1704067200000
}
```

### 错误码定义

| Code | 说明 |
|------|------|
| 200 | 成功 |
| 40001 | 参数校验失败 |
| 40002 | 文件类型不支持 |
| 40003 | 文件大小超限 |
| 40004 | 重复的简历文件 |
| 40101 | 未登录 |
| 40301 | 无权限 |
| 42901 | AI调用次数超限 |
| 50001 | 系统内部错误 |
| 50002 | AI服务不可用 |
| 50003 | 文件存储失败 |

---

### 模块一：用户认证 (Auth)

#### 1.1 用户注册
```
POST /api/v1/auth/register

Request Body:
{
  "username": "string, 4-20字符, 必填",
  "password": "string, 6-20字符, 必填",
  "email": "string, 邮箱格式, 必填",
  "role": "string, 可选, 默认HR, 枚举: HR/INTERVIEWER"
}

Response:
{
  "code": 200,
  "data": {
    "userId": 10001,
    "username": "zhangsan"
  }
}

后端实现要点:
- 密码使用BCrypt加密存储
- 校验用户名和邮箱唯一性
- 生成默认的每日AI配额
```

#### 1.2 用户登录
```
POST /api/v1/auth/login

Request Body:
{
  "username": "string, 必填",
  "password": "string, 必填"
}

Response:
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 7200,
    "userInfo": {
      "userId": 10001,
      "username": "zhangsan",
      "role": "HR",
      "dailyAiQuota": 100,
      "todayAiUsed": 15
    }
  }
}

后端实现要点:
- 校验用户名密码
- 生成JWT Token (Access Token 2小时, Refresh Token 7天)
- 【Redis】记录登录态，支持踢人下线
```

#### 1.3 刷新Token
```
POST /api/v1/auth/refresh

Request Body:
{
  "refreshToken": "string, 必填"
}

Response: 同登录接口
```

---

### 模块二：简历管理 (Resume)

#### 2.1 上传简历 ⭐ 核心接口
```
POST /api/v1/resumes/upload
Content-Type: multipart/form-data

Request:
- file: 文件, 必填, 支持 pdf/docx/doc, 最大10MB

Response:
{
  "code": 200,
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "resumeId": 12345,
    "fileName": "张三-Java开发.pdf",
    "status": "QUEUED",
    "message": "文件已上传，正在排队等待解析"
  }
}

后端实现要点 (重点！):
1. 计算文件MD5 Hash
2. 【Redis】检查 dedup:resume:{hash} 是否存在，存在则返回"重复文件"错误
3. 保存文件到磁盘/OSS
4. 写入 resumes 表，status = PENDING
5. 【Redis】设置 task:resume:{taskId} = {status: QUEUED, resumeId: xxx}
6. 【Redis】设置 dedup:resume:{hash} = resumeId, TTL 7天
7. 【RabbitMQ】发送消息到 resume.parse.queue
8. 立即返回 taskId，不等待解析完成
```

#### 2.2 批量上传简历
```
POST /api/v1/resumes/batch-upload
Content-Type: multipart/form-data

Request:
- files: 文件数组, 必填, 最多20个文件

Response:
{
  "code": 200,
  "data": {
    "totalCount": 20,
    "successCount": 18,
    "failedCount": 2,
    "tasks": [
      { "taskId": "xxx", "fileName": "张三.pdf", "status": "QUEUED" },
      { "taskId": null, "fileName": "重复文件.pdf", "status": "DUPLICATE", "message": "文件已存在" }
    ]
  }
}

后端实现要点:
- 【Redis】上传频率限流: rate:upload:{userId} 每分钟最多5次批量上传
- 循环处理每个文件，单个失败不影响其他
```

#### 2.3 查询解析状态 ⭐ 核心接口
```
GET /api/v1/resumes/tasks/{taskId}/status

Response (处理中):
{
  "code": 200,
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PROCESSING",
    "progress": 60,
    "message": "正在提取简历信息..."
  }
}

Response (处理完成):
{
  "code": 200,
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "SUCCESS",
    "progress": 100,
    "resumeId": 12345,
    "candidateId": 67890,
    "candidateName": "张三",
    "message": "解析完成"
  }
}

后端实现要点:
- 【Redis】直接从 task:resume:{taskId} 读取，不查数据库
- 如果Redis中不存在，再fallback查MySQL
```

#### 2.4 获取简历列表
```
GET /api/v1/resumes

Query Parameters:
- page: int, 默认1
- size: int, 默认20, 最大100
- status: string, 可选, 筛选状态
- keyword: string, 可选, 搜索文件名

Response:
{
  "code": 200,
  "data": {
    "total": 156,
    "pages": 8,
    "list": [
      {
        "resumeId": 12345,
        "fileName": "张三-Java开发.pdf",
        "fileSize": 102400,
        "status": "SUCCESS",
        "candidateId": 67890,
        "candidateName": "张三",
        "uploadTime": "2024-01-01 10:30:00"
      }
    ]
  }
}
```

#### 2.5 重新解析简历
```
POST /api/v1/resumes/{resumeId}/reparse

Response:
{
  "code": 200,
  "data": {
    "taskId": "new-task-id",
    "message": "已重新提交解析任务"
  }
}

后端实现要点:
- 只允许对 FAILED 状态的简历重新解析
- 重置 retry_count
- 重新发送MQ消息
```

---

### 模块三：候选人管理 (Candidate)

#### 3.1 获取候选人详情
```
GET /api/v1/candidates/{candidateId}

Response:
{
  "code": 200,
  "data": {
    "candidateId": 67890,
    "resumeId": 12345,
    "name": "张三",
    "phone": "138****1234",
    "email": "zhang***@gmail.com",
    "experienceYears": 5,
    "highestEducation": "本科",
    "graduateSchool": "浙江大学",
    "currentCompany": "阿里巴巴",
    "currentPosition": "高级Java工程师",
    "skills": ["Java", "Spring Boot", "MySQL", "Redis", "Kafka"],
    "aiSummary": "5年Java后端开发经验，精通分布式系统，有大厂背景",
    "workExperiences": [...],
    "educationHistory": [...]
  }
}

后端实现要点:
- 【Redis】先查 cache:candidate:{id}，缓存30分钟
- 手机号/邮箱做脱敏处理
```

#### 3.2 获取候选人列表
```
GET /api/v1/candidates

Query Parameters:
- page: int
- size: int
- skills: string, 可选, 逗号分隔的技能筛选
- experienceMin: int, 可选
- experienceMax: int, 可选
- education: string, 可选

Response: 分页列表
```

#### 3.3 AI智能搜索候选人 ⭐ 核心接口
```
POST /api/v1/candidates/smart-search

Request Body:
{
  "query": "帮我找一个精通Spring Boot和Redis，有3年以上经验的Java开发",
  "filters": {
    "experienceMin": 3,
    "education": "本科"
  },
  "topK": 10
}

Response:
{
  "code": 200,
  "data": {
    "queryAnalysis": "用户需要: Java开发, 核心技能Spring Boot+Redis, 3年+经验",
    "candidates": [
      {
        "candidateId": 67890,
        "name": "张三",
        "matchScore": 92,
        "matchReasons": ["5年Java经验", "精通Spring Boot", "3年Redis使用经验"],
        "concerns": [],
        "skills": ["Java", "Spring Boot", "Redis", "MySQL"],
        "currentPosition": "高级Java工程师"
      },
      {
        "candidateId": 67891,
        "name": "李四",
        "matchScore": 78,
        "matchReasons": ["4年Java经验", "熟悉Spring Boot"],
        "concerns": ["Redis经验较少"],
        "skills": ["Java", "Spring Boot", "MySQL"],
        "currentPosition": "Java开发工程师"
      }
    ]
  }
}

后端实现要点 (最复杂的接口！):
1. 【Redis限流】检查 rate:ai:{userId}:{date}，超过每日配额返回429错误
2. 【MySQL】根据filters进行初步筛选，获取候选范围
3. 【Spring AI】将query转换为向量 (Embedding)
4. 【向量数据库】执行相似度搜索，获取Top K相似的候选人
5. 【Spring AI】调用LLM对候选人进行精细排序和理由分析
6. 【Redis】AI调用成功后，INCR rate:ai:{userId}:{date}
7. 返回结果
```

---

### 模块四：职位管理 (Job)

#### 4.1 创建职位
```
POST /api/v1/jobs

Request Body:
{
  "title": "高级Java工程师",
  "department": "技术部",
  "description": "负责核心业务系统开发...",
  "requirements": "1. 5年以上Java开发经验\n2. 精通Spring Boot...",
  "requiredSkills": ["Java", "Spring Boot", "MySQL"],
  "salaryMin": 25,
  "salaryMax": 40,
  "experienceMin": 5,
  "education": "本科"
}

Response:
{
  "code": 200,
  "data": {
    "jobId": 1001,
    "status": "DRAFT"
  }
}
```

#### 4.2 获取职位详情
```
GET /api/v1/jobs/{jobId}

后端实现要点:
- 【Redis】先查 cache:job:{jobId}，缓存30分钟
```

#### 4.3 发布/关闭职位
```
PUT /api/v1/jobs/{jobId}/status

Request Body:
{
  "status": "PUBLISHED"
}

后端实现要点:
- 【Redis】状态变更后删除缓存 cache:job:{jobId}
```

#### 4.4 获取热门职位
```
GET /api/v1/jobs/hot

Query Parameters:
- limit: int, 默认10

后端实现要点:
- 【Redis】从 cache:job:hot (ZSet) 获取
- ZSet存储 jobId，score为热度值（浏览量+申请量）
- 缓存10分钟
```

#### 4.5 AI职位匹配推荐
```
POST /api/v1/jobs/{jobId}/match-candidates

Request Body:
{
  "topK": 20
}

Response:
{
  "code": 200,
  "data": {
    "jobTitle": "高级Java工程师",
    "matches": [
      {
        "candidateId": 67890,
        "matchScore": 88,
        "matchReasons": ["技能匹配度高", "经验符合要求"]
      }
    ]
  }
}

后端实现要点:
- 将职位JD转换为向量
- 在向量库中搜索最相似的候选人
- 【Redis限流】同样消耗AI配额
```

---

### 模块五：申请与面试 (Application)

#### 5.1 投递简历到职位
```
POST /api/v1/applications

Request Body:
{
  "jobId": 1001,
  "candidateId": 67890
}

后端实现要点:
- 【Redis分布式锁】lock:application:{jobId}:{candidateId}，防止重复投递
- 异步计算匹配度 (可选，通过MQ)
```

#### 5.2 获取职位的申请列表
```
GET /api/v1/jobs/{jobId}/applications

Query Parameters:
- status: string, 可选
- sortBy: string, 默认 match_score desc
```

#### 5.3 更新申请状态
```
PUT /api/v1/applications/{applicationId}/status

Request Body:
{
  "status": "INTERVIEW",
  "notes": "简历不错，安排技术面"
}
```

#### 5.4 创建面试
```
POST /api/v1/interviews

Request Body:
{
  "applicationId": 5001,
  "interviewerId": 200,
  "round": 1,
  "interviewType": "VIDEO",
  "scheduledAt": "2024-01-15 14:00:00",
  "durationMinutes": 60
}
```

#### 5.5 提交面试反馈
```
PUT /api/v1/interviews/{interviewId}/feedback

Request Body:
{
  "feedback": "技术扎实，沟通能力强...",
  "score": 8,
  "recommendation": "YES"
}
```

---

### 模块六：数据统计 (Statistics)

#### 6.1 获取招聘概览
```
GET /api/v1/statistics/overview

Response:
{
  "code": 200,
  "data": {
    "totalJobs": 25,
    "publishedJobs": 18,
    "totalCandidates": 1560,
    "todayUploads": 23,
    "pendingApplications": 89,
    "todayInterviews": 5
  }
}
```

#### 6.2 获取AI使用统计
```
GET /api/v1/statistics/ai-usage

Response:
{
  "code": 200,
  "data": {
    "dailyQuota": 100,
    "todayUsed": 45,
    "remaining": 55,
    "usageHistory": [
      { "date": "2024-01-10", "count": 67 },
      { "date": "2024-01-11", "count": 89 }
    ]
  }
}

后端实现要点:
- 【Redis】读取 rate:ai:{userId}:{date}
```

---

## 🎯 核心代码挑战 (你需要实现的难点)

### 挑战一：Redis Lua 脚本实现滑动窗口限流

**需求描述:**
实现一个 `@RateLimiter` 注解，可以灵活配置限流规则。需要支持:
- 每分钟限制 N 次
- 每天限制 M 次
- 使用 Redis Lua 脚本保证原子性

**验收标准:**
```java
@RateLimiter(key = "ai:search", limit = 5, window = 60, windowUnit = TimeUnit.SECONDS)
@RateLimiter(key = "ai:daily", limit = 100, window = 1, windowUnit = TimeUnit.DAYS)
public SearchResult smartSearch(SearchRequest request) {
    // ...
}
```

**提示:**
- 使用 AOP 切面拦截带注解的方法
- Lua 脚本需要实现: INCR + EXPIRE 的原子操作
- 考虑使用滑动窗口算法 (ZSet) 或简单计数器 (String)

---

### 挑战二：RabbitMQ 消费者幂等性与重试机制

**需求描述:**
实现简历解析的 MQ 消费者，需要满足:
1. **幂等性**: 同一条消息无论消费几次，结果一致
2. **重试机制**: 解析失败自动重试3次，超过3次进入死信队列
3. **分布式锁**: 防止多个消费者同时处理同一份简历

**验收标准:**
```java
@RabbitListener(queues = "resume.parse.queue")
public void handleResumeParseMessage(ResumeParseMessage message) {
    // 1. 检查Redis中是否已处理过
    // 2. 获取Redisson分布式锁
    // 3. 调用Spring AI解析
    // 4. 更新数据库和Redis状态
    // 5. 异常处理与重试逻辑
}
```

**提示:**
- 使用 `message.getMessageProperties().getDeliveryTag()` 手动ACK
- 配置 `spring.rabbitmq.listener.simple.retry.*` 实现自动重试
- 死信队列需要单独配置监听器处理

---

### 挑战三：Spring AI 结构化输出与 Prompt Engineering

**需求描述:**
设计 Prompt 让 LLM 稳定输出符合 Java Bean 结构的 JSON，用于简历信息提取。

**验收标准:**
```java
public record ResumeExtraction(
    String name,
    String phone,
    String email,
    Integer experienceYears,
    List<String> skills,
    List<WorkExperience> workExperiences
) {}

// 调用示例
ResumeExtraction result = aiService.extractResumeInfo(pdfContent);
```

**提示:**
- Spring AI 支持 `BeanOutputConverter` 自动解析 JSON 到 Java 对象
- Prompt 中需要明确 JSON Schema
- 处理 LLM 输出不稳定的情况（重试、fallback）

---

### 挑战四：向量搜索与 Metadata Filtering

**需求描述:**
实现 RAG 搜索时的混合查询:
1. 向量相似度搜索 (语义匹配)
2. 元数据过滤 (经验年限 >= 3, 学历 = 本科)

**验收标准:**
```java
public List<Candidate> hybridSearch(String query, SearchFilter filter) {
    // 1. 将query转换为embedding
    // 2. 在向量库中执行: 向量相似度 + metadata过滤
    // 3. 返回Top K结果
}
```

**提示:**
- 如果使用 Milvus: 支持在 search 时传入 `expr` 表达式
- 如果使用 PgVector: 可以使用 SQL WHERE 条件
- Spring AI 的 `VectorStore` 接口支持 `FilterExpression`

---

### 挑战五：文件去重与分布式锁的优雅实现

**需求描述:**
实现上传时的文件去重检测，要求:
1. 计算文件 MD5 作为唯一标识
2. 使用 Redis 检查是否已存在
3. 使用 Redisson 分布式锁防止并发上传同一文件

**验收标准:**
```java
public UploadResult uploadResume(MultipartFile file) {
    String fileHash = calculateMD5(file);
    
    // 1. 检查Redis去重标记
    // 2. 获取分布式锁
    // 3. double-check是否存在
    // 4. 保存文件和数据库记录
    // 5. 释放锁
}
```

**提示:**
- MD5 计算使用 `DigestUtils.md5DigestAsHex()`
- Redisson 锁使用 `RLock lock = redissonClient.getLock("lock:resume:" + hash)`
- 注意锁的超时时间设置，防止死锁

---

## 📁 建议的项目结构

```
smartats/
├── pom.xml
├── src/main/java/com/yourcompany/smartats/
│   ├── SmartAtsApplication.java
│   ├── common/
│   │   ├── result/
│   │   │   ├── Result.java
│   │   │   └── ResultCode.java
│   │   ├── exception/
│   │   │   ├── BusinessException.java
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── annotation/
│   │   │   └── RateLimiter.java
│   │   └── aspect/
│   │       └── RateLimiterAspect.java
│   ├── config/
│   │   ├── RedisConfig.java
│   │   ├── RabbitMQConfig.java
│   │   ├── SecurityConfig.java
│   │   └── SpringAIConfig.java
│   ├── module/
│   │   ├── auth/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── dto/
│   │   │   └── security/
│   │   ├── resume/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── consumer/          # RabbitMQ消费者
│   │   │   ├── dto/
│   │   │   └── entity/
│   │   ├── candidate/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── dto/
│   │   │   └── entity/
│   │   ├── job/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── dto/
│   │   │   └── entity/
│   │   └── ai/
│   │       ├── service/
│   │       │   ├── ResumeExtractionService.java
│   │       │   ├── SemanticSearchService.java
│   │       │   └── EmbeddingService.java
│   │       └── prompt/
│   │           └── PromptTemplates.java
│   └── infrastructure/
│       ├── redis/
│       │   └── RedisService.java
│       ├── mq/
│       │   └── MessagePublisher.java
│       └── storage/
│           └── FileStorageService.java
└── src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    ├── application-prod.yml
    └── mapper/
        └── *.xml
```

---

## 🐳 部署架构 (Docker Compose)

为了快速搭建开发环境，建议使用 `docker-compose.yml` 管理基础设施依赖。

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: smartats
    ports:
      - "3306:3306"
    volumes:
      - ./data/mysql:/var/lib/mysql

  redis:
    image: redis:7.0
    ports:
      - "6379:6379"
    volumes:
      - ./data/redis:/data

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: user
      RABBITMQ_DEFAULT_PASS: password

  milvus: # 向量数据库 (可选 PgVector)
    image: milvusdb/milvus:v2.3.4
    ports:
      - "19530:19530"
    # 注意: Milvus 生产环境需要 etcd 和 minio，此处简化

  # 建议增加 MinIO 用于简历文件存储
  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
```

---

## 📚 学习资源

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Redisson 分布式锁](https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers)
- [RabbitMQ 死信队列](https://www.rabbitmq.com/dlx.html)
- [MyBatis-Plus 官方文档](https://baomidou.com/)

---

**文档版本:** v1.0  
**最后更新:** 2026-02-14
**作者:** AI Copilot
