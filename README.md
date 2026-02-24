# SmartATS — 智能招聘管理系统

<p align="center">
  <b>基于 Spring Boot 3 + 智谱AI + Milvus 的 AI 驱动 ATS 系统</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-blue" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen" alt="Spring Boot 3.2.5">
  <img src="https://img.shields.io/badge/Spring%20AI-1.0.0--M4-orange" alt="Spring AI">
  <img src="https://img.shields.io/badge/Tests-190%20passed-success" alt="Tests">
  <img src="https://img.shields.io/badge/API%20Endpoints-40-blue" alt="API">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

---

## 项目简介

**SmartATS**（Smart Applicant Tracking System）是一套面向 HR 的智能招聘管理系统。系统支持简历上传、AI 自动解析结构化信息、完整招聘流程管理（职位申请、面试安排）、RAG 语义候选人搜索以及 Webhook 事件通知。

> 📌 这是一个**学习项目**，旨在实践 Spring Boot 3 全栈开发、AI 集成、分布式系统设计等技术。

### 核心特性

- **AI 简历解析** — 上传 PDF/DOC/DOCX 简历，智谱AI 自动提取姓名、技能、工作经历等结构化数据
- **异步处理管道** — RabbitMQ 消息队列 + Redisson 分布式锁 + 重试机制 + 死信队列
- **RAG 语义搜索** — Milvus 向量数据库 + embedding-3 嵌入模型，自然语言搜索候选人
- **完整招聘流程** — 职位发布 → 简历解析 → 候选人管理 → 投递申请 → 面试安排 → 反馈
- **Webhook 通知** — 12 种事件类型，HMAC-SHA256 签名，异步发送 + 重试
- **Redis 缓存体系** — Cache-aside 模式 + 延迟双删 + 原子计数器 + 热门排行

### 业务链路

```
简历上传 → MD5 去重 → MinIO 存储 → RabbitMQ 异步 → AI 结构化提取 → 向量化入库 → Webhook 通知
    ↓
语义搜索 → Query Embedding → Milvus ANN 检索 → 分数过滤 → MySQL 补全 → RAG 响应
```

---

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 核心框架 | Spring Boot | 3.2.5 |
| 运行时 | JDK | 21 |
| ORM | MyBatis-Plus | 3.5.10.1 |
| 数据库 | MySQL | 8.0 |
| 缓存 & 分布式锁 | Redis + Redisson | 7.0 / 3.25.0 |
| 消息队列 | RabbitMQ | 3.12 |
| 文件存储 | MinIO | 8.5.10 |
| 向量数据库 | Milvus | 2.4.17 |
| AI 集成 | Spring AI + 智谱AI | 1.0.0-M4 |
| 文档解析 | Apache POI + PDFBox | 5.2.5 / 2.0.29 |
| 安全认证 | Spring Security + JWT | jjwt 0.11.5 |
| API 文档 | SpringDoc OpenAPI | 2.5.0 |
| 测试 | JUnit 5 + Mockito + MockMvc | 190 测试用例 |

---

## 系统架构

```
                        Spring Security + JWT 认证过滤器
                                    |
    +--------+--------+--------+----+----+--------+--------+--------+
    |        |        |             |        |        |        |
  认证     职位    简历上传     候选人     申请     面试    Webhook
  模块     模块    + AI解析    + 搜索     模块     模块     模块
                    |              |
              RabbitMQ          Milvus
             异步解析管道       向量搜索
             DLX → DLQ         ANN 检索
                    |
              解析消费者
              Redisson 分布式锁
              智谱AI 结构化提取  ──▶ MinIO (文件)
              向量嵌入 & 入库    ──▶ MySQL (数据)
              Webhook 事件通知  ──▶ Milvus (向量)
                                ──▶ Redis (缓存)
```

---

## 快速启动

### 环境要求

| 工具 | 版本要求 |
|------|----------|
| JDK | 21 |
| Maven | 3.9+ |
| Docker Desktop | 最新版 |

### 第一步：启动基础设施

```bash
git clone https://github.com/NissonCX/SmartATS.git
cd SmartATS

# 启动全部服务（MySQL、Redis、RabbitMQ、MinIO、Milvus）
docker-compose up -d

# 查看服务状态
docker-compose ps
```

> **注意**：Milvus 首次启动需要约 90 秒，且内存占用约 1~2GB。如不需要向量搜索功能，可以只启动核心服务：
> ```bash
> docker-compose up -d mysql redis rabbitmq minio
> ```

| 服务 | 地址 | 用途 |
|------|------|------|
| MySQL | `localhost:3307` | 业务数据库 |
| Redis | `localhost:6379` | 缓存 & 分布式锁 |
| RabbitMQ | `localhost:5672` / `localhost:15672`（管理界面） | 消息队列 |
| MinIO | `localhost:9000` / `localhost:9001`（控制台） | 文件存储 |
| Milvus | `localhost:19530` | 向量数据库 |

### 第二步：配置环境变量

```bash
cp .env.example .env
```

编辑 `.env` 文件，填入必需的配置：

```env
# 必填：智谱AI API Key（https://open.bigmodel.cn 获取）
ZHIPU_API_KEY=your_api_key_here

# 可选：邮件功能（QQ 邮箱 SMTP）
MAIL_USERNAME=your_email@foxmail.com
MAIL_PASSWORD=your_smtp_auth_code
```

### 第三步：初始化数据库

```bash
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < docker/mysql/init/01-init-database.sql
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < src/main/resources/db/webhook_tables.sql
```

### 第四步：构建并运行

```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

应用启动后：
- API 地址：`http://localhost:8080/api/v1`
- Swagger 文档：`http://localhost:8080/api/v1/swagger-ui.html`

---

## API 接口概览

共 **40 个** REST API 端点，完整文档见 Swagger UI。

| 模块 | 端点数 | 路径前缀 | 说明 |
|------|:------:|---------|------|
| 认证 | 5 | `/auth` | 注册、登录、JWT 刷新、邮箱验证码 |
| 职位 | 8 | `/jobs` | CRUD、发布/关闭、热门排行 |
| 简历 | 5 | `/resumes` | 单文件/批量上传、解析状态查询 |
| 候选人 | 5 | `/candidates` | CRUD、多维筛选、数据脱敏 |
| 职位申请 | 6 | `/applications` | 创建申请、状态流转、多维查询 |
| 面试 | 5 | `/interviews` | 安排、反馈、取消 |
| Webhook | 4 | `/webhooks` | CRUD、测试发送 |
| 智能搜索 | 2 | `/smart-search` | RAG 语义候选人搜索 |

**认证方式**：除登录/注册外，所有接口需在请求头携带 `Authorization: Bearer <token>`

---

## 数据库设计

8 张核心表：

```
users ──┐
        ├──▶ jobs ──────────────────────────────┐
        │                                       │
        └──▶ resumes ──▶ candidates ────────────┤
                               └──▶ job_applications ──▶ interview_records

webhook_configs ──▶ webhook_logs
```

| 表名 | 说明 |
|------|------|
| `users` | 用户账号（ADMIN / HR / INTERVIEWER） |
| `jobs` | 职位信息（DRAFT → PUBLISHED → CLOSED） |
| `resumes` | 简历文件（MD5 去重，唯一索引 `file_hash`） |
| `candidates` | AI 提取的结构化候选人数据 |
| `job_applications` | 投递记录（PENDING → SCREENING → INTERVIEW → OFFER/REJECTED） |
| `interview_records` | 面试记录（多轮次、评分、推荐等级） |
| `webhook_configs` | Webhook 配置（事件类型、签名密钥） |
| `webhook_logs` | Webhook 投递日志 |

---

## 项目结构

```
src/main/java/com/smartats/
├── SmartAtsApplication.java
├── common/                     # 公共组件
│   ├── constants/              # Redis Key 常量
│   ├── enums/                  # 状态枚举（6 个）
│   ├── exception/              # BusinessException + 全局异常处理
│   ├── result/                 # Result<T> + ResultCode
│   └── util/                   # 文件校验、数据脱敏
├── config/                     # 配置类（8 个）
│   ├── SecurityConfig.java     # Spring Security + CORS 配置化
│   ├── RabbitMQConfig.java     # MQ 拓扑
│   ├── MilvusConfig.java       # Milvus 向量数据库
│   ├── ZhipuAiConfig.java      # 智谱AI（OpenAI 兼容模式）
│   └── ...
├── infrastructure/             # 基础设施层
│   ├── email/                  # 邮件服务
│   ├── mq/                     # 消息发布
│   ├── storage/                # MinIO 文件存储
│   └── vector/                 # 嵌入 + 向量存储（Milvus）
└── module/                     # 业务模块（8 个）
    ├── auth/                   # 认证（注册/登录/JWT/验证码）
    ├── job/                    # 职位管理
    ├── resume/                 # 简历上传 + AI 解析
    ├── candidate/              # 候选人 + 智能搜索
    ├── application/            # 职位申请
    ├── interview/              # 面试管理
    └── webhook/                # Webhook 通知
```

---

## 测试

```bash
# 运行全部测试（排除需要 Docker 的 MinIO 集成测试）
mvn test -Dtest='!com.smartats.MinioFileStorageServiceTest'

# 运行指定模块测试
mvn test -Dtest='com.smartats.module.auth.**'
```

当前状态：**190 个测试用例，19 个测试类，全部通过** ✅

覆盖范围：
- **Service 层单元测试**（Mockito）：UserService、JobService、ResumeService、CandidateService、ApplicationService、InterviewService、WebhookService、SmartSearchService、EmbeddingService、CandidateVectorService、VectorStoreService
- **Controller 集成测试**（MockMvc）：AuthController、JobController、ResumeController、CandidateController、SmartSearchController、WebhookController

---

## 开发规范

| 规范 | 要求 |
|------|------|
| 数据库操作 | 统一使用 `LambdaQueryWrapper`（类型安全） |
| 异常处理 | 统一抛出 `BusinessException(ResultCode.xxx)` |
| Redis 操作 | 使用 `StringRedisTemplate` + 手动 JSON 序列化 |
| Redis Key | 统一使用 `RedisKeyConstants.*` 常量 |
| 缓存策略 | 读：Cache-aside；写：删缓存 + 延迟双删 |
| 文件上传 | 必须经过 `FileValidationUtil` 校验（Content-Type + Magic Number） |
| 认证信息 | 通过 `Authentication.getPrincipal()` 获取 userId |
| 日志 | INFO=业务里程碑，WARN=潜在问题，ERROR=系统错误，禁止记录密码 |

---

## 参考文档

| 文档 | 说明 |
|------|------|
| [设计文档](docs/SmartATS-Design-Document.md) | 完整技术规范、数据库 Schema、API 定义 |
| [开发教学手册](docs/SmartATS-从0到1开发教学手册.md) | 分阶段开发指南 |
| [部署指南](docs/deployment-guide.md) | Docker Compose 部署、Nginx、监控、备份 |
| [项目进度](docs/project-progress-summary.md) | 模块完成情况分析 |

---

## License

MIT License

---

**最后更新**：2026 年 2 月 ｜ **版本**：1.0.0 ｜ **状态**：功能完整
