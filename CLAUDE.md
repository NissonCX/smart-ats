# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**SmartATS** is an intelligent recruitment management system for HR professionals. The system enables resume uploads, AI-powered automatic parsing of structured information (via 智谱AI), full recruitment workflow management (applications, interviews), and Webhook event notifications.

**Current State** (2026-02-25):
- ✅ All 11 business modules implemented and functional (including vector search)
- ✅ 47 API endpoints across auth, job, resume, candidate, application, interview, webhook, smart-search, audit, analytics
- ✅ AI resume parsing complete (智谱AI via Spring AI OpenAI-compatible mode)
- ✅ Async processing pipeline (RabbitMQ + Redisson distributed lock + retry + DLQ)
- ✅ Redis caching with cache-aside pattern, delayed double-delete, atomic counters
- ✅ Comprehensive code quality optimization (34 issues fixed: security, N+1, exceptions, MQ)
- ✅ Spring Security with JWT + configurable CORS + role-based access
- ✅ Milvus vector database + RAG semantic candidate search (embedding-3, 1024 dim)
- ✅ Swagger/OpenAPI configured (SpringDoc 2.5.0, JWT Bearer scheme)
- ✅ 193 unit/integration tests across 19 test classes (Service + Controller layers)
- ✅ Environment profiles: dev / test / prod
- ✅ Production deployment documentation
- ✅ **NEW** 审计日志模块（AOP + Custom Annotation 自动采集）
- ✅ **NEW** AI 智能匹配打分（多维度匹配：语义相似度30% + 技能匹配35% + 经验匹配20% + 学历匹配15%）
- ✅ **NEW** 招聘漏斗分析 + SSE 实时推送

**Overall Progress: 100%** (all TODOs resolved, production-ready)

## Technology Stack

| Component | Technology | Version | Status |
|-----------|-----------|---------|--------|
| Core Framework | Spring Boot | 3.2.5 | ✅ |
| Runtime | JDK | 21 | ✅ Required |
| ORM | MyBatis-Plus | 3.5.10.1 | ✅ |
| Database | MySQL | 8.0 | ✅ |
| Cache | Redis | 7.0 | ✅ StringRedisTemplate |
| Distributed Lock | Redisson | 3.25.0 | ✅ Implemented |
| Message Queue | RabbitMQ | 3.12 | ✅ |
| File Storage | MinIO | 8.5.10 | ✅ |
| AI Integration | Spring AI + 智谱AI | 1.0.0-M4 | ✅ |
| Document Parsing | Apache POI + PDFBox | 5.2.5 / 2.0.29 | ✅ |
| Security | Spring Security + JWT (jjwt 0.11.5) | - | ✅ |
| Email | Spring Mail | - | ✅ |
| JSON | Fastjson2 | 2.0.43 | ✅ |
| Utilities | Hutool | 5.8.23 | ✅ |
| Vector Database | Milvus | 2.4.17 (SDK 2.4.8) | ✅ Implemented |
| API Docs | SpringDoc OpenAPI | 2.5.0 | ✅ Implemented |
| AOP | Spring Boot Starter AOP | 3.2.5 | ✅ Audit Logging |
| SSE | Spring MVC SseEmitter | 3.2.5 | ✅ Real-time Push |
| Testing | JUnit 5 + Mockito + MockMvc | - | ✅ 193 tests |

## Development Environment Setup

### Required Software
1. JDK 21
2. Maven 3.9+
3. Docker Desktop

### Starting Infrastructure

```bash
# Start all infrastructure services
docker-compose up -d

# Check services status
docker-compose ps
```

| Service | Port | Credentials |
|---------|------|-------------|
| MySQL | 3307 | smartats / smartats123 |
| Redis | 6379 | password: redis123 |
| RabbitMQ | 5672 (AMQP), 15672 (UI) | admin / admin123, VHost: smartats |
| MinIO | 9000 (API), 9001 (Console) | admin / admin123456 |

### Build and Run

```bash
mvn clean install
mvn spring-boot:run
```

### Database Initialization

```bash
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < docker/mysql/init/01-init-database.sql
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < src/main/resources/db/webhook_tables.sql
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < src/main/resources/db/audit_logs.sql
```

### Environment Variables

Create `.env` file (already in `.gitignore`):
```env
ZHIPU_API_KEY=your_api_key_here
ZHIPU_MODEL=glm-4-flash-250414
MAIL_HOST=smtp.qq.com
MAIL_PORT=587
MAIL_USERNAME=your_email@qq.com
MAIL_PASSWORD=your_qq_smtp_auth_code
JWT_SECRET=your_production_secret_key_minimum_32_characters
```

## Module Structure

```
src/main/java/com/smartats/
├── SmartAtsApplication.java
├── common/                                 # Shared (10 files)
│   ├── annotation/AuditLog.java            # Custom audit annotation
│   ├── aspect/AuditLogAspect.java          # AOP Around advice for audit
│   ├── constants/RedisKeyConstants.java    # All Redis key prefixes
│   ├── exception/                          # BusinessException + GlobalExceptionHandler
│   ├── handler/JsonTypeHandler.java        # MyBatis JSON type handler
│   ├── result/                             # Result<T> + ResultCode (error codes: 10xxx~43xxx)
│   └── util/                               # FileValidationUtil + DataMaskUtil
├── config/                                 # Configuration (8 files)
│   ├── SecurityConfig.java                 # Spring Security + CORS + JWT filter + whitelist
│   ├── RabbitMQConfig.java                 # Exchange, Queue, DLX, DLQ
│   ├── MinioConfig.java                    # MinIO client
│   ├── AsyncConfig.java                    # @Async thread pools (asyncExecutor, webhookExecutor)
│   ├── RedissonConfig.java                 # Redisson distributed lock client
│   ├── ZhipuAiConfig.java                  # 智谱AI (OpenAI-compatible)
│   ├── MilvusConfig.java                   # Milvus vector database client
│   └── OpenApiConfig.java                  # SpringDoc + JWT Bearer scheme
├── infrastructure/                         # Infrastructure (6 files)
│   ├── email/EmailService.java             # HTML email with verification codes
│   ├── mq/MessagePublisher.java            # RabbitMQ message publishing
│   ├── storage/                            # FileStorageService interface + MinIO impl
│   └── vector/                             # EmbeddingService + VectorStoreService (Milvus)
├── common/                                 # Shared (10 files)
│   ├── annotation/AuditLog.java            # Custom audit annotation
│   ├── aspect/AuditLogAspect.java          # AOP Around advice for audit
│   ├── constants/RedisKeyConstants.java    # All Redis key prefixes
│   ├── exception/                          # BusinessException + GlobalExceptionHandler
│   ├── handler/JsonTypeHandler.java        # MyBatis JSON type handler
│   ├── result/                             # Result<T> + ResultCode (error codes: 10xxx~43xxx)
│   └── util/                               # FileValidationUtil + DataMaskUtil
└── module/                                 # Business modules (11 modules, 90+ files)
    ├── auth/          (12 files)           # ✅ Register, Login, JWT, Refresh, Verification
    ├── job/           (10 files)           # ✅ CRUD, Cache, Hot Ranking, View Count Sync
    ├── resume/        (11 files)           # ✅ Upload, Dedup, AI Parse, MQ Consumer
    ├── candidate/     (12 files)           # ✅ CRUD, Filter, Cache, Masking, Vector
    ├── application/   (11 files)           # ✅ Create, Status Flow, AI Match Score, Multi-Query
    ├── interview/     (7 files)            # ✅ Schedule, Feedback, Cancel, Query
    ├── webhook/       (10 files)           # ✅ CRUD, Test, 12 Event Types, HMAC Signing
    ├── audit/         (5 files)            # ✅ NEW - AOP Audit Log (entity, mapper, service, controller, DTO)
    └── analytics/     (7 files)            # ✅ NEW - Recruitment Funnel, SSE Push, Redis Cache
```

## Architecture Overview

### Processing Chains

1. **Authentication Chain**: Register → Verify Email → Login → JWT Token → Redis Storage → Refresh
2. **Upload Chain**: Upload → MD5 Hash → Dedup Check → MinIO Storage → DB → MQ → AI Parse → Candidate DB → Webhook
3. **Job Management Chain**: Create → Cache → Publish → View Count (Redis INCR) → Hot Ranking (ZSet) → Periodic DB Sync (GETDEL)
4. **Recruitment Chain**: Create Application → Status Flow (PENDING→REVIEWING→INTERVIEW→OFFER/REJECTED) → Schedule Interview → Feedback
5. **Webhook Chain**: Event Trigger → @Async Send → HMAC-SHA256 Signature → Retry → Log
6. **Search Chain**: ✅ Query Embedding → Milvus ANN Search → Score Filter → MySQL Enrich → RAG Response
7. **Audit Chain**: @AuditLog Annotation → AOP Intercept → Extract User/IP/Params → Mask Sensitive Fields → @Async DB Write
8. **Match Score Chain**: Create Application → @Async Score Calculation (Semantic 30% + Skill 35% + Experience 20% + Education 15%) → Save Score + Reasons
9. **Analytics Chain**: Aggregate SQL → Redis Cache (5min) → REST API → SSE Push (ApplicationEvent → EventListener → Broadcast)

### Async Processing Flow (Resume)

```
Upload → MD5 Hash → Redis Dedup → MinIO Storage → DB Record → Task Status (Redis) → MQ Message → Return taskId
                                                       ↓
                                     Consumer → Idempotency Check → Redisson Lock → Extract Content (POI/PDFBox)
                                       → AI Parse (智谱AI) → Save Candidate → Webhook Notify → Task Complete
                                       ↓ (fail, retryCount < 3)
                                     Republish with retryCount+1, ACK original
                                       ↓ (retryCount >= 3)
                                     NACK → DLQ (smartats.dlx → resume.parse.dlq)
```

### Caching Strategy

- **Read**: Redis first → fallback MySQL → populate cache (30min TTL)
- **Write**: Update MySQL → delete cache → CacheEvictionService async delayed double-delete (500ms)
- **Hot data**: ZSet for rankings (10min TTL)
- **Atomic counters**: Redis INCR + periodic GETDEL sync to DB (prevents count loss)

### Distributed Locking

Redisson with Watchdog — used in `ResumeParseConsumer` for fileHash-level locking during AI parsing.

## Database Schema

### 9 Tables

| Table | Status | Notes |
|-------|--------|-------|
| `users` | ✅ | User accounts with roles (ADMIN/HR/INTERVIEWER), AI quota |
| `jobs` | ✅ | Job postings with JSON fields, full-text index |
| `resumes` | ✅ | Resume files with MD5 deduplication (unique index on file_hash) |
| `candidates` | ✅ | AI-extracted structured data (skills JSON, work_experiences JSON) |
| `job_applications` | ✅ | Application tracking with status flow and match scores |
| `interview_records` | ✅ | Interview records with rounds, scores, recommendations |
| `webhook_configs` | ✅ | Webhook configuration with event types and secrets |
| `webhook_logs` | ✅ | Webhook delivery logs |
| `audit_logs` | ✅ | **NEW** Operation audit logs (user, module, operation, IP, duration) |

### Key Relationships

- `resumes.file_hash` — Unique index for deduplication
- `candidates.resume_id` → `resumes.id` (1:1)
- `job_applications.job_id` → `jobs.id`
- `job_applications.candidate_id` → `candidates.id`
- `interview_records.application_id` → `job_applications.id`

## Redis Key Patterns

All key prefixes are centralized in `RedisKeyConstants.java`.

| Pattern | Type | Purpose | TTL |
|---------|------|---------|-----|
| `jwt:token:{userId}` | String | Access Token | 2h |
| `jwt:refresh:{userId}` | String | Refresh Token | 7d |
| `verification_code:{email}` | String | Email verification code | 5min |
| `verification_code-limit:{email}` | String | Rate limit for sending codes | 60s |
| `task:resume:{taskId}` | String | Resume parsing task status | 24h |
| `idempotent:resume:{resumeId}` | String | Idempotency check for MQ | 1h |
| `dedup:resume:{fileHash}` | String | File deduplication mark | 7d |
| `lock:resume:{fileHash}` | String | Redisson distributed lock | auto-release |
| `cache:job:{jobId}` | String | Job detail cache | 30min |
| `cache:job:hot` | ZSet | Hot job ranking | 10min |
| `counter:job:view:{jobId}` | String | Atomic view counter | persistent |
| `cache:candidate:{candidateId}` | String | Candidate cache | 30min |
| `cache:application:{appId}` | String | Application cache | 30min |
| `cache:interview:{interviewId}` | String | Interview cache | 30min |
| `rate:upload:{userId}` | String | Batch upload rate limit | 60s |
| `cache:analytics:overview:{params}` | String | Analytics overview cache | 5min |

## RabbitMQ Topology

- **Exchange**: `smartats.exchange` (Direct, durable)
- **Queue**: `resume.parse.queue`
- **DLX**: `smartats.dlx` (Dead Letter Exchange)
- **DLQ**: `resume.parse.dlq`
- **Routing Key**: `resume.parse`
- **Retry**: Republish with incremented retryCount (max 3), then NACK to DLQ
- **Message**: JSON with `taskId`, `resumeId`, `filePath`, `fileHash`, `uploaderId`, `retryCount`

## API Endpoints Summary (47 total)

### Authentication — 5 endpoints
| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| POST | `/api/v1/auth/register` | ❌ | Register (BCrypt, ADMIN role blocked) |
| POST | `/api/v1/auth/login` | ❌ | Login (accessToken 2h + refreshToken 7d) |
| POST | `/api/v1/auth/send-verification-code` | ❌ | Send email verification code |
| POST | `/api/v1/auth/refresh` | ❌ | Refresh token |
| GET | `/api/v1/auth/test` | ✅ | Test auth status |

### Jobs — 8 endpoints
| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| POST | `/api/v1/jobs` | ✅ | Create job |
| PUT | `/api/v1/jobs` | ✅ | Update job |
| GET | `/api/v1/jobs/{id}` | ❌ | Get job detail (cached) |
| GET | `/api/v1/jobs` | ❌ | List jobs (paginated) |
| POST | `/api/v1/jobs/{id}/publish` | ✅ | Publish job |
| POST | `/api/v1/jobs/{id}/close` | ✅ | Close job |
| DELETE | `/api/v1/jobs/{id}` | ✅ | Delete job |
| GET | `/api/v1/jobs/hot` | ❌ | Hot jobs ranking |

### Resumes — 5 endpoints
| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| POST | `/api/v1/resumes/upload` | ✅ | Upload resume (PDF/DOC/DOCX, ≤10MB) |
| POST | `/api/v1/resumes/batch-upload` | ✅ | Batch upload (max 20, rate-limited 5/min) |
| GET | `/api/v1/resumes/tasks/{taskId}` | ✅ | Get parse task status |
| GET | `/api/v1/resumes/{id}` | ✅ | Get resume detail |
| GET | `/api/v1/resumes` | ✅ | List resumes (paginated) |

### Candidates — 5 endpoints
| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| GET | `/api/v1/candidates/resume/{resumeId}` | ✅ | Get candidate by resume ID |
| GET | `/api/v1/candidates/{id}` | ✅ | Get candidate detail (cached) |
| PUT | `/api/v1/candidates/{id}` | ✅ | Update candidate (@Valid) |
| DELETE | `/api/v1/candidates/{id}` | ✅ | Delete candidate |
| GET | `/api/v1/candidates` | ✅ | List (multi-filter + data masking) |

### Applications — 7 endpoints
| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| POST | `/api/v1/applications` | ✅ | Create application (dedup + auto AI matching) |
| PUT | `/api/v1/applications/{id}/status` | ✅ | Update status |
| POST | `/api/v1/applications/{id}/match-score` | ✅ | **NEW** Recalculate AI match score |
| GET | `/api/v1/applications/{id}` | ✅ | Get application detail |
| GET | `/api/v1/applications/job/{jobId}` | ✅ | List by job |
| GET | `/api/v1/applications/candidate/{candidateId}` | ✅ | List by candidate |
| GET | `/api/v1/applications` | ✅ | List (paginated) |

### Interviews — 5 endpoints
| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| POST | `/api/v1/interviews` | ✅ | Schedule interview |
| PUT | `/api/v1/interviews/{id}/feedback` | ✅ | Submit feedback |
| POST | `/api/v1/interviews/{id}/cancel` | ✅ | Cancel interview |
| GET | `/api/v1/interviews/{id}` | ✅ | Get interview detail |
| GET | `/api/v1/interviews/application/{appId}` | ✅ | List by application |

### Webhooks — 4 endpoints
| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| POST | `/api/v1/webhooks` | ✅ | Create webhook |
| GET | `/api/v1/webhooks` | ✅ | List webhooks |
| DELETE | `/api/v1/webhooks/{id}` | ✅ | Delete webhook |
| POST | `/api/v1/webhooks/{id}/test` | ✅ | Test webhook |

### Audit Logs — 1 endpoint
| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| GET | `/api/v1/audit-logs` | ✅ | **NEW** Query audit logs (multi-filter, paginated) |

### Analytics — 3 endpoints
| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| GET | `/api/v1/analytics/overview` | ✅ | **NEW** Recruitment funnel + KPI overview (5min cache) |
| GET | `/api/v1/analytics/stream` | ✅ | **NEW** SSE real-time push (30min timeout, 30s heartbeat) |
| GET | `/api/v1/analytics/sse-status` | ✅ | **NEW** Active SSE connection count |

## Webhook Event Types (12)

`resume.uploaded`, `resume.parse_completed`, `resume.parse_failed`, `candidate.created`, `candidate.updated`, `application.submitted`, `application.status_changed`, `interview.scheduled`, `interview.completed`, `interview.cancelled`, `system.error`, `system.maintenance`

## Development Guidelines

### ⚠️ Critical Rules

1. **Use StringRedisTemplate**, NOT `RedisTemplate<String, Object>`
   - Manual JSON serialization with ObjectMapper

2. **Use LambdaQueryWrapper**, NOT string-based QueryWrapper
   - Type-safe and refactor-friendly

3. **Always use `BusinessException(ResultCode.xxx)`** for business errors
   - Never throw raw `RuntimeException` or `IllegalArgumentException`
   - GlobalExceptionHandler catches all exceptions

4. **File uploads MUST use FileValidationUtil**
   - Content-Type + Magic Number validation
   - Filename sanitization (path traversal prevention)

5. **JWT extraction**: Use `Authentication.getPrincipal()` (Spring Security way)
   - Returns `Long userId` from SecurityContext

6. **Redis Key prefixes**: Always use `RedisKeyConstants.*` constants
   - Never hardcode key strings

7. **@Async methods must be in a separate class** (not self-call)
   - Use `CacheEvictionService` pattern for delayed double-delete
   - Spring AOP proxy only works on inter-bean calls

### Code Quality Standards

```java
// ✅ Correct Redis usage
String json = objectMapper.writeValueAsString(obj);
stringRedisTemplate.opsForValue().set(key, json, ttl, TimeUnit.SECONDS);

// ✅ Correct exception handling
throw new BusinessException(ResultCode.NOT_FOUND, "候选人不存在");

// ✅ Correct transaction annotation
@Transactional(rollbackFor = Exception.class)
```

### Logging Convention
- INFO: Business milestones
- WARN: Potential issues
- ERROR: System errors (never log passwords or sensitive data)
- DEBUG: Detailed info (disabled in prod)

## Known Issues and TODOs

### Resolved ✅
- ~~Test Coverage ~0%~~ → 190 tests across 19 test classes (Service + Controller layers)
- ~~Vector Search Not Implemented~~ → Milvus + RAG semantic candidate search (embedding-3, 1024 dim)
- ~~Swagger/OpenAPI Not Configured~~ → SpringDoc 2.5.0, all 8 controllers annotated with @Tag + @Operation
- ~~Environment Separation~~ → dev / test / prod profiles configured
- ~~LoginResponse.todayAiUsed TODO~~ → Implemented via Redis `rate:ai:{userId}:{date}`
- ~~Batch Upload Missing~~ → `POST /resumes/batch-upload` (max 20 files, rate-limited 5/min)
- ~~CORS Configuration~~ → Configurable via `smartats.cors.allowed-origins`, prod reads `CORS_ALLOWED_ORIGINS` env
- ~~MinIO Integration Test~~ → Annotated with `@Tag("integration")` + `@DisabledIfEnvironmentVariable(CI=true)`
- ~~Deployment Documentation~~ → `docs/deployment-guide.md` (Docker Compose, env vars, health checks, backup)

### No remaining issues — all TODOs resolved ✅

## Reference Documentation

- `docs/project-progress-summary.md` — Detailed module analysis and statistics
- `docs/next-steps-plan-2026-02-23.md` — Development priorities and technical decisions
- `docs/SmartATS-Design-Document.md` — Complete technical specification, database schema, API definitions
- `docs/SmartATS-从0到1开发教学手册.md` — Step-by-step development tutorial
- `docs/deployment-guide.md` — Production deployment guide (Docker Compose, Nginx, monitoring)

## Git Workflow

### Commit Message Format (Chinese)

```
type(scope): 中文描述

详细说明（可选）
```

Types: `feat`, `fix`, `docs`, `refactor`, `perf`, `security`, `test`, `chore`

### Recent Commits

| Hash | Description |
|------|-------------|
| `b31725f` | refactor: 全面优化代码质量（34项问题） |
| `2f8e0fd` | feat(application,interview): 职位申请和面试记录模块 |
| `6290576` | feat(candidate): 高级筛选、Redis缓存、脱敏 |
| `23fe2ea` | feat(auth/webhook): RefreshToken 刷新 + Webhook 测试 |
| `6f5beb6` | feat(resume/candidate): AI 简历解析及候选人模块 |
