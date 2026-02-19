# SmartATS — 智能招聘管理系统

> 面向 HR 的 AI 驱动简历解析与人才搜索平台

---

## 目录

- [项目简介](#项目简介)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [数据库设计](#数据库设计)
- [目录结构](#目录结构)
- [快速启动](#快速启动)
- [API 接口文档](#api-接口文档)
- [Redis 规范](#redis-规范)
- [RabbitMQ 拓扑](#rabbitmq-拓扑)
- [AI 集成设计](#ai-集成设计)
- [开发规范](#开发规范)
- [开发进度与路线图](#开发进度与路线图)

---

## 项目简介

**SmartATS**（Smart Applicant Tracking System）是一套面向 HR 的智能招聘管理系统，核心链路分为四条：

| 链路 | 功能描述 |
|------|---------|
| **登录链路** | 注册 / 登录 / JWT 鉴权，三种角色（ADMIN / HR / INTERVIEWER） |
| **上传链路** | 批量上传简历 → MD5 去重 → MinIO 存储 → MQ 异步解析 → AI 结构化提取 |
| **检索链路** | 关键词筛选 + RAG 语义搜索（向量相似度 + LLM 重排）候选人 |
| **招聘流程链路** | 职位管理 → 简历投递 → 面试安排 → 面试反馈 |

---

## 技术栈

| 层次 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 核心框架 | Spring Boot | 3.1.6 | 基础框架 |
| 运行时 | JDK | 21 | Java 运行环境 |
| ORM | MyBatis-Plus | 3.5.9 | 数据库操作（LambdaQueryWrapper） |
| 数据库 | MySQL | 8.0 | 业务数据持久化，含全文索引 |
| 缓存 / 限流 / 锁 | Redis | 7.0 | 缓存、Lua 原子限流、分布式去重 |
| 分布式锁 | Redisson | — | Watchdog 自动续期分布式锁 |
| 消息队列 | RabbitMQ | 3.12 | 简历解析任务异步解耦、死信补偿 |
| 对象存储 | MinIO | latest | 简历文件存储 |
| AI 集成 | Spring AI | — | LLM 调用（GPT / DeepSeek / Ollama）+ Embedding |
| 向量数据库 | Milvus / PgVector | — | 简历语义向量存储与检索 |
| 认证 | Spring Security + JWT | — | 接口鉴权、BCrypt 密码加密 |
| 邮件 | Spring Mail | — | HTML 验证码邮件（QQ SMTP） |
| JSON 工具 | Fastjson2 | 2.0.43 | 序列化 |
| 工具库 | Hutool | 5.8.23 | 加密、时间、字符串 |

---

## 系统架构

```
┌──────────┐     ┌────────────────────────────────────────────────────────────┐
│ HR Client│────▶│  API 层：Spring Security JWT 过滤器 + @RateLimiter(RedisLua) │
└──────────┘     └──────────────────────────┬───────────────────────────────┘
                                            │
        ┌───────────────────────────────────┼──────────────────────────────┐
        ▼                                   ▼                              ▼
┌──────────────┐                   ┌──────────────────┐           ┌──────────────┐
│   认证模块    │                   │    简历模块        │           │   职位模块   │
│  注册 登录    │                   │  批量上传/去重     │           │  CRUD + 缓存 │
│  JWT 工具    │                   │  MD5 → MinIO      │           │  热榜 ZSet   │
│  JWT 过滤器  │                   │  状态轮询          │           └──────────────┘
└──────────────┘                   └────────┬──────────┘
                                            │ 发 MQ 消息
                                            ▼
                                   ┌──────────────────┐
                                   │    RabbitMQ       │
                                   │  resume.parse     │
                                   │  .queue           │
                                   │  DLX → DLQ 死信   │
                                   └────────┬──────────┘
                                            │ 消费
                                            ▼
                                   ┌────────────────────────┐
                                   │  解析消费者              │
                                   │  1. 幂等检查            │
                                   │  2. Redisson 分布式锁   │
                                   │  3. Spring AI 结构化提取│
                                   │  4. 写 MySQL candidates │
                                   │  5. Embedding → 向量库  │
                                   │  6. 更新 Redis 任务状态 │
                                   └────────────────────────┘
                                            │
       ┌────────────────────────────────────┼──────────────────────────┐
       ▼                                    ▼                          ▼
 ┌──────────┐                       ┌──────────────┐           ┌──────────────┐
 │  MySQL   │                       │    Redis      │           │  MinIO +     │
 │ users    │                       │ task:*        │           │  向量库       │
 │ jobs     │                       │ rate:*        │           │  简历文件     │
 │ resumes  │                       │ lock:*        │           │  embedding   │
 │candidates│                       │ cache:*       │           └──────────────┘
 │applications                      │ dedup:*       │
 │interviews│                       └──────────────┘
 └──────────┘
```

---

## 数据库设计

共 6 张核心表，建表顺序按依赖关系推进：

### 表关系概览

```
users ──┐
        ├──▶ jobs ──────────────────────────────────────────┐
        │                                                   │
        └──▶ resumes ──▶ candidates                         │
                                  └──▶ job_applications ──▶ interview_records
```

### 各表说明

| 表名 | 用途 | 关键字段 |
|------|------|---------|
| `users` | 账号体系 | `role`（ADMIN/HR/INTERVIEWER），`daily_ai_quota` 每日 AI 配额 |
| `jobs` | 职位信息 | `status`（DRAFT/PUBLISHED/CLOSED），`required_skills` JSON，全文索引 |
| `resumes` | 简历文件 | `file_hash` MD5 唯一索引，`status`（PENDING/QUEUED/PROCESSING/SUCCESS/FAILED），`retry_count` |
| `candidates` | AI 提取结构化数据 | `skills` JSON，`work_experiences` JSON，`education_history` JSON，`vector_id` |
| `job_applications` | 投递记录 | `match_score` AI 匹配分，联合唯一索引 `(job_id, candidate_id)` |
| `interview_records` | 面试记录 | `round` 轮次，`recommendation`（STRONG_YES/YES/NEUTRAL/NO/STRONG_NO） |

---

## 目录结构

```
src/main/java/com/smartats/
├── SmartAtsApplication.java                # 启动类
├── common/                                 # 公共组件
│   ├── annotation/                         # @RateLimiter 自定义注解
│   ├── aspect/                             # 限流 AOP 切面（Redis Lua 原子操作）
│   ├── constants/                          # 常量定义
│   ├── exception/                          # BusinessException、GlobalExceptionHandler
│   └── result/                             # Result<T> 统一响应、ResultCode 错误码枚举
├── config/                                 # 配置类
│   ├── SecurityConfig.java                 # Spring Security 过滤链白名单
│   └── MinioConfig.java                    # MinIO 客户端配置
├── infrastructure/                         # 基础设施服务
│   ├── email/                              # HTML 验证码邮件（EmailService）
│   └── storage/                            # MinIO 文件上传/下载（FileStorageService）
└── module/                                 # 业务模块
    ├── auth/                               # 认证模块（✅ 完成）
    │   ├── controller/                     # AuthController
    │   ├── dto/                            # RegisterRequest LoginRequest LoginResponse
    │   ├── entity/                         # User
    │   ├── filter/                         # JwtAuthenticationFilter
    │   ├── mapper/                         # UserMapper
    │   ├── service/                        # UserService
    │   └── util/                           # JwtUtil
    ├── resume/                             # 简历模块（✅ 完成）
    │   ├── controller/                     # ResumeController
    │   ├── dto/                            # UploadResponse TaskStatusResponse
    │   ├── entity/                         # Resume
    │   ├── mapper/                         # ResumeMapper
    │   └── service/                        # ResumeService（MD5去重 MQ投递）
    └── job/                                # 职位模块（✅ 完成）
        ├── controller/                     # JobController
        ├── dto/                            # CreateJobRequest JobDetailResponse
        ├── entity/                         # Job
        ├── mapper/                         # JobMapper
        ├── service/                        # JobService（Redis缓存策略）
        └── sync/                           # 热榜同步定时任务

# ── 待新增 ──────────────────────────────────────────────────────
# module/candidate/     候选人详情/列表/AI语义搜索（⏳ 待开发）
# module/application/   投递申请/状态流转（⏳ 待开发）
# module/interview/     面试安排/反馈（⏳ 待开发）
# module/statistics/    招聘概览/AI配额统计（⏳ 待开发）
# infrastructure/mq/    RabbitMQ 消费者（幂等/重试/DLQ）（⏳ 开发中）
# infrastructure/ai/    Spring AI 封装（Prompt/Embedding/RAG）（⏳ 待开发）
```

---

## 快速启动

### 前置依赖

| 工具 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 21 | 必须，低版本不兼容 Text Block 等语法 |
| Maven | 3.9+ | 构建工具 |
| Docker Desktop | 最新版 | 运行所有基础设施服务 |

### 第一步：启动基础设施

```bash
git clone <repo-url>
cd SmartATS

# 一键启动 MySQL / Redis / RabbitMQ / MinIO
docker-compose up -d

# 验证服务健康
docker-compose ps
```

| 服务 | 地址 | 账号 / 密码 |
|------|------|------------|
| MySQL | `localhost:3307` | `smartats` / `smartats123` |
| Redis | `localhost:6379` | 密码：`redis123` |
| RabbitMQ 管理界面 | `http://localhost:15672` | `admin` / `admin123`，VHost：`smartats` |
| MinIO 控制台 | `http://localhost:9001` | `admin` / `admin123456` |

### 第二步：初始化数据库

Docker 启动时自动执行 `docker/mysql/init/` 下的 SQL 脚本。手动执行：

```bash
docker exec -i smartats-mysql mysql -usmartats -psmartats123 smartats \
  < docker/mysql/init/init.sql
```

### 第三步：配置环境变量

邮件功能需要 QQ 邮箱 SMTP 授权码（**勿提交到 Git**）：

```bash
# 在 IDE Run Configuration 中配置，或写入 .env（加入 .gitignore）
export MAIL_PASSWORD=你的QQ邮箱SMTP授权码
```

`application.yml` 通过 `${MAIL_PASSWORD}` 读取。

### 第四步：构建并运行

```bash
mvn clean install -DskipTests
mvn spring-boot:run
# 或: java -jar target/smartats-1.0.0.jar
```

应用启动后监听：`http://localhost:8080/api/v1`

---

## API 接口文档

**Base URL：** `/api/v1`

**认证方式：** 除注册/登录外，所有接口需在请求头携带：
```
Authorization: Bearer <accessToken>
```

**统一响应格式：**
```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1704067200000
}
```

### 错误码

| Code | 说明 |
|------|------|
| 200 | 成功 |
| 40001 | 参数校验失败 |
| 40002 | 文件类型不支持 |
| 40003 | 文件大小超限（最大 10MB） |
| 40004 | 重复的简历文件（MD5 已存在） |
| 40101 | 未登录 / Token 过期 |
| 40301 | 无权限 |
| 42901 | AI 调用次数超限（每日配额） |
| 50001 | 系统内部错误 |
| 50002 | AI 服务不可用 |
| 50003 | 文件存储失败 |

---

### 模块一：认证 `/auth`

| 方法 | 路径 | 说明 | 需要 Token |
|------|------|------|:---------:|
| `POST` | `/auth/register` | 用户注册（BCrypt 密码加密） | ❌ |
| `POST` | `/auth/login` | 登录，返回 accessToken（2h）+ refreshToken（7d） | ❌ |
| `POST` | `/auth/refresh` | 用 refreshToken 换新 Token 对 | ❌ |
| `POST` | `/auth/send-code` | 发送邮箱验证码（HTML 模板，5 分钟有效） | ❌ |

**登录响应 data 示例：**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "expiresIn": 7200,
  "userInfo": {
    "userId": 10001,
    "username": "zhangsan",
    "role": "HR",
    "dailyAiQuota": 100,
    "todayAiUsed": 15
  }
}
```

---

### 模块二：简历 `/resumes`

| 方法 | 路径 | 说明 | 需要 Token |
|------|------|------|:---------:|
| `POST` | `/resumes/upload` | 单文件上传（PDF/DOCX/DOC，≤ 10MB） | ✅ |
| `POST` | `/resumes/batch-upload` | 批量上传（最多 20 个，限流：每分钟 5 次） | ✅ |
| `GET` | `/resumes/tasks/{taskId}/status` | 查询解析任务状态（优先读 Redis） | ✅ |
| `GET` | `/resumes` | 简历列表（分页、状态筛选、文件名搜索） | ✅ |
| `POST` | `/resumes/{resumeId}/reparse` | 重新解析 FAILED 状态的简历 | ✅ |

**单文件上传处理流程：**

```
接收文件 → 校验类型/大小
  → 计算 MD5 Hash
  → 查 Redis dedup:resume:{hash}（存在 → 返回 40004 重复错误）
  → 上传到 MinIO
  → 写入 resumes 表（status = PENDING）
  → 写 Redis task:resume:{taskId}（status = QUEUED，TTL 24h）
  → 写 Redis dedup:resume:{hash}（TTL 7d）
  → 发送 MQ 消息（ResumeParseMessage）
  → 立即返回 taskId（不阻塞等待 AI 解析）
```

**任务状态流转（前端按 taskId 轮询）：**

```
QUEUED → PROCESSING → SUCCESS
                 └──→ FAILED（可通过 /reparse 重新触发）
```

---

### 模块三：候选人 `/candidates`

| 方法 | 路径 | 说明 | 需要 Token |
|------|------|------|:---------:|
| `GET` | `/candidates/{id}` | 详情（Redis 缓存 30min，手机/邮箱脱敏） | ✅ |
| `GET` | `/candidates` | 列表（技能、工作年限、学历多维度筛选，分页） | ✅ |
| `POST` | `/candidates/smart-search` | **RAG 语义搜索**（自然语言 → 向量召回 → LLM 重排） | ✅ |

**智能搜索请求示例：**
```json
{
  "query": "帮我找一个精通Spring Boot和Redis，3年以上经验的Java开发",
  "filters": { "experienceMin": 3, "education": "本科" },
  "topK": 10
}
```

**智能搜索响应示例：**
```json
{
  "queryAnalysis": "需求: Java开发, 核心技能 Spring Boot+Redis, 3年+经验",
  "candidates": [
    {
      "candidateId": 67890,
      "name": "张三",
      "matchScore": 92,
      "matchReasons": ["5年Java经验", "精通Spring Boot", "3年Redis使用经验"],
      "concerns": [],
      "currentPosition": "高级Java工程师"
    }
  ]
}
```

**智能搜索后端链路：**

```
Redis 限流检查（rate:ai:{userId}:{date}）
  → MySQL filters 初步筛选
  → Spring AI: query → Embedding 向量
  → 向量库 Top-K 相似度召回
  → Spring AI LLM: 精细排序 + matchScore + matchReasons + concerns
  → Redis INCR 配额计数
  → 返回排序结果
```

---

### 模块四：职位 `/jobs`

| 方法 | 路径 | 说明 | 需要 Token |
|------|------|------|:---------:|
| `POST` | `/jobs` | 创建职位（初始 DRAFT 状态） | ✅ |
| `GET` | `/jobs/{id}` | 详情（先查 Redis cache:job:{id}，缓存 30min） | ✅ |
| `PUT` | `/jobs/{id}/status` | 发布/关闭职位（变更后**删除**对应缓存） | ✅ |
| `GET` | `/jobs/hot` | 热门职位（ZSet 热度排行，缓存 10min） | ✅ |
| `GET` | `/jobs` | 职位列表（分页、状态筛选） | ✅ |
| `POST` | `/jobs/{id}/match-candidates` | AI 职位匹配：JD 向量化后在候选人库搜索 Top-K | ✅ |

---

### 模块五：申请与面试 `/applications` `/interviews`

| 方法 | 路径 | 说明 | 需要 Token |
|------|------|------|:---------:|
| `POST` | `/applications` | 投递简历到职位（Redisson 锁防重复，联合唯一索引兜底） | ✅ |
| `GET` | `/jobs/{jobId}/applications` | 职位申请列表（默认按 match_score DESC 排序） | ✅ |
| `PUT` | `/applications/{id}/status` | 更新申请状态（SCREENING/INTERVIEW/OFFER/REJECTED） | ✅ |
| `POST` | `/interviews` | 创建面试安排（指定轮次、面试官、时间、形式） | ✅ |
| `PUT` | `/interviews/{id}/feedback` | 提交面试反馈（评分 1-10 + 五档推荐级别） | ✅ |

---

### 模块六：统计 `/statistics`

| 方法 | 路径 | 说明 | 需要 Token |
|------|------|------|:---------:|
| `GET` | `/statistics/overview` | 招聘概览（总职位数、候选人数、今日上传、待处理申请等） | ✅ |
| `GET` | `/statistics/ai-usage` | AI 配额统计（读 Redis rate:ai:* 计数器） | ✅ |

---

## Redis 规范

### Key 命名一览

| Key 模式 | 类型 | 用途 | TTL |
|----------|------|------|-----|
| `task:resume:{taskId}` | Hash | 解析任务状态（status/progress/message/resumeId） | 24h |
| `dedup:resume:{fileHash}` | String | 文件 MD5 去重标记 | 7d |
| `lock:resume:{fileHash}` | String | 解析分布式锁（Redisson Watchdog 自动续期） | 30s |
| `lock:application:{jobId}:{candidateId}` | String | 防重复投递锁 | 5min |
| `rate:ai:{userId}:{yyyyMMdd}` | String（计数器） | 每日 AI 调用配额 | 24h |
| `rate:ai:minute:{userId}:{yyyyMMddHHmm}` | String（计数器） | 每分钟 AI 调用限流 | 60s |
| `rate:upload:{userId}` | String（计数器） | 批量上传频率（每分钟 5 次） | 1min |
| `cache:job:{jobId}` | String（JSON） | 职位详情缓存 | 30min |
| `cache:job:hot` | ZSet | 热门职位排行（score = 热度值） | 10min |
| `cache:candidate:{id}` | String（JSON） | 候选人信息缓存 | 30min |

### 限流注解（AOP + Lua）

使用 Redis Lua 脚本保证 `INCR + EXPIRE` 原子性，通过 `@RateLimiter` 注解统一声明：

```java
@RateLimiter(key = "ai:search:minute", limit = 5, window = 60, windowUnit = TimeUnit.SECONDS)
@RateLimiter(key = "ai:daily", limit = 100, window = 1, windowUnit = TimeUnit.DAYS)
public SearchResult smartSearch(SearchRequest request) { ... }
```

超出限额统一返回错误码 `42901`。

---

## RabbitMQ 拓扑

```
Producer
  └──▶ smartats.exchange（Direct Exchange，durable）
         │
         │  routing_key: resume.parse
         ▼
       resume.parse.queue（主队列）
         参数：message-ttl=30min，max-length=10000
         参数：x-dead-letter-exchange=smartats.dlx
         │
         │  Consumer 手动 ACK
         │    1. 幂等检查（避免重复消费）
         │    2. Redisson 锁（fileHash 维度，Watchdog 续期）
         │    3. 更新 Redis 状态 PROCESSING
         │    4. Spring AI 提取结构化数据
         │    5. 写 MySQL（candidates + resumes）
         │    6. Embedding → 向量库
         │    7. 更新 Redis 状态 SUCCESS → ACK
         │
         │  处理异常 → NACK（requeue=false）→ 死信
         ▼
       smartats.dlx（Dead Letter Exchange）
         │
         │  routing_key: resume.parse.failed
         ▼
       resume.parse.dlq（死信队列）
         └──▶ 定时任务扫描 / 告警通知 / 人工介入
```

**消息体（ResumeParseMessage）：**
```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "resumeId": 12345,
  "filePath": "resumes/2024/01/xxx.pdf",
  "fileHash": "abc123md5...",
  "uploaderId": 100,
  "timestamp": 1704067200000,
  "retryCount": 0
}
```

---

## AI 集成设计

### 简历结构化提取 Prompt

```
[System]
你是专业简历解析助手。从简历文本提取结构化 JSON，字段缺失设为 null，
不添加任何额外解释或 markdown 标记。

输出格式：
{
  "name": "", "phone": "", "email": "",
  "experienceYears": 5,
  "skills": ["Java", "Spring Boot", "Redis"],
  "workExperiences": [
    { "company":"", "position":"", "startDate":"", "endDate":"", "description":"" }
  ],
  "educationHistory": [
    { "school":"", "degree":"", "major":"", "startDate":"", "endDate":"" }
  ],
  "summary": "一句话核心竞争力"
}

[User]
请解析以下简历内容：
{resumeContent}
```

### RAG 语义搜索链路

```
用户自然语言查询
  → Spring AI Embedding（query → 向量）
  → 向量库 Top-K 相似度召回（支持元数据过滤：年限/学历/技能）
  → Spring AI LLM 重排（生成 matchScore + matchReasons + concerns）
  → 返回排序后的候选人列表
```

### AI 调用治理

| 治理手段 | 说明 |
|---------|------|
| 超时控制 | AI 调用设置超时，超时任务标记 FAILED |
| 每日配额 | Redis `rate:ai:{userId}:{date}` 计数，超限返回 42901 |
| 重试上限 | `resumes.retry_count` 记录，超上限进入 DLQ |
| 错误追踪 | 所有 AI 异常写 ERROR 日志，包含 taskId 便于溯源 |
| 幂等消费 | 消费前检查任务状态，防止重复处理 |

---

## 开发规范

### 数据库操作

```java
// ✅ 正确：LambdaQueryWrapper（类型安全，重构友好）
userMapper.selectOne(new LambdaQueryWrapper<User>()
    .eq(User::getUsername, username));

// ❌ 错误：字符串形式（运行时才报错，重构不友好）
userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
```

### 核心规范

- 业务异常统一抛出 `BusinessException(ResultCode.xxx)`
- 密码必须 BCrypt 加密，响应中**禁止**返回密码字段
- 多步数据库操作使用 `@Transactional(rollbackFor = Exception.class)`
- **写操作后删除缓存**（而非更新），防止脏读

### 日志规范

| 级别 | 使用场景 |
|------|---------|
| `INFO` | 业务里程碑（上传成功、解析完成） |
| `WARN` | 潜在问题（缓存频繁未命中、重试次数增加） |
| `ERROR` | 系统级错误（AI 调用失败、MQ 消息处理异常） |
| `DEBUG` | 详细调试信息（生产环境关闭） |

### 开发顺序原则

> **"先开门，再进屋"** —— Spring Security 必须最先配置，否则所有请求返回 401

1. Spring Security 配置（放行 register/login）
2. 数据库建表
3. 认证模块（注册/登录/JWT）
4. 职位模块（熟悉 CRUD + Redis 缓存）
5. 简历上传（同步快速返回链路）
6. MQ 消费者（异步解析链路）
7. AI 提取 + 向量入库
8. 候选人模块 + RAG 语义搜索
9. 申请与面试流程
10. 限流注解 + Lua 脚本 + DLQ 补偿

---

## 开发进度与路线图

### 当前状态（2026-02-19）

| 模块 | 状态 |
|------|------|
| 项目骨架（统一响应 / 全局异常 / ResultCode） | ✅ 完成 |
| Spring Security 配置 | ✅ 完成 |
| 认证模块（注册 / 登录 / JWT 过滤器） | ✅ 完成 |
| 邮箱验证码（HTML 模板） | ✅ 完成 |
| MinIO 文件存储（FileStorageService） | ✅ 完成 |
| 简历上传模块（上传 / MD5去重 / MQ投递 / 状态查询） | ✅ 完成 |
| 职位管理模块（CRUD / Redis缓存 / 热榜ZSet） | ✅ 完成 |
| `@RateLimiter` 限流注解（AOP + Redis Lua） | ⏳ 开发中 |
| MQ 消费者（解析 / 幂等 / 重试 / DLQ） | ⏳ 开发中 |
| AI 结构化提取（Spring AI + Prompt 模板） | ⏳ 第4周 |
| 向量入库（Embedding → Milvus/PgVector） | ⏳ 第4周 |
| 候选人模块（详情 / 列表 / Redis缓存 / 脱敏） | ⏳ 第3周 |
| RAG 语义搜索（向量召回 + LLM 重排） | ⏳ 第4周 |
| 申请与面试流程 | ⏳ 第5周 |
| 数据统计模块 | ⏳ 第5周 |
| DLQ 死信补偿定时任务 | ⏳ 第5-6周 |

### 里程碑计划

| 周次 | 目标 |
|------|------|
| 第 1 周 | 环境搭建 + 骨架 + 数据库建表 + 认证模块 ✅ |
| 第 2 周 | 职位管理 + 候选人基础查询 + Redis 缓存体系 |
| 第 3 周 | 简历上传完整链路 + MQ 消费者 + 任务状态追踪 |
| 第 4 周 | AI 提取稳定输出 + Embedding 向量入库 + 基础语义搜索 |
| 第 5 周 | 申请面试流程 + 限流 Lua 脚本 + 统计模块 |
| 第 6 周 | DLQ 补偿 + 监控日志 + 性能调优 |

---

## 参考文档

| 文档 | 说明 |
|------|------|
| [SmartATS 设计文档](docs/SmartATS-Design-Document.md) | 完整技术规范：数据库 Schema、全量 API 定义、Redis / MQ / AI 设计 |
| [从0到1开发教学手册](docs/SmartATS-从0到1开发教学手册.md) | 分阶段开发指南、新手踩坑清单、每章验收标准 |
| [简历上传模块开发指南](docs/简历上传模块开发指南.md) | 上传链路详细实现步骤与注意事项 |
| [简历上传模块代码审查报告](docs/简历上传模块代码审查报告.md) | 代码质量问题汇总与改进建议 |
| [职位管理模块实现指南](docs/职位管理模块实现指南.md) | 职位 CRUD + Redis 缓存写入与失效策略 |
| [JWT 认证过滤器实现指南](docs/JWT认证过滤器实现指南.md) | JwtAuthenticationFilter 实现细节 |
| [环境信息](docs/环境信息.md) | Docker 服务连接信息速查 |
