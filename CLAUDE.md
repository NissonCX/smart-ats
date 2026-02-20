# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**SmartATS** is an intelligent recruitment management system for HR professionals. The system enables batch resume uploads, AI-powered automatic parsing of structured information, and RAG semantic talent search.

**Current State** (2026-02-20):
- ✅ Project skeleton established (Spring Boot 3.1.6 + MyBatis-Plus 3.5.9)
- ✅ Authentication module complete (95%) - register, login, verification code, JWT auth
- ✅ Job management module complete (90%) - CRUD, caching, view count, hot jobs
- ✅ Resume upload module complete (80%) - upload, deduplication, async parsing framework
- ✅ Webhook module complete (70%) - event subscription, signature verification
- ✅ Spring Security configured with CORS support
- ✅ RabbitMQ integration complete (exchange, queue, DLQ, consumer)
- ✅ MinIO file storage integrated
- ✅ Redis integration complete (StringRedisTemplate, caching, atomic counters)
- ✅ Global exception handling and unified response wrapper
- ✅ Security enhancements (file validation, CORS, webhook signature)

**Overall Progress: ~80%**

**Completed Modules**:
- `common/` - Unified response wrapper, global exception handler, error codes, file validation utilities
- `module/auth/` - Complete auth module (controller, service, mapper, entity, DTOs, JWT filter, verification)
- `module/job/` - Complete job module (CRUD, caching, view count sync, hot jobs ranking)
- `module/resume/` - Resume upload with async processing (MQ consumer framework ready)
- `module/webhook/` - Webhook management with event subscription
- `infrastructure/` - Email, MQ, storage services

**Partially Completed**:
- `module/resume/consumer` - Framework ready, AI parsing logic needed
- `module/webhook/service` - HTTP sending logic needs testing

**Not Started**:
- `module/candidate/` - Candidate management and search
- `module/interview/` - Interview scheduling and records
- AI integration (Spring AI for resume parsing)
- Vector database integration (Milvus/PgVector)

**Next Steps** (Priority Order):
1. **Fix compilation error** - Add jakarta.validation dependency for @URL annotation
2. **Implement AI resume parsing** - Integrate Spring AI, extract structured candidate data
3. **Implement Redisson distributed lock** - Replace TODO in ResumeParseConsumer
4. **Complete Webhook test function** - Implement test endpoint in WebhookController
5. **Add unit tests** - Critical business logic needs test coverage
6. **Implement Candidate module** - Display AI-extracted structured data
7. **Configure environment separation** - Dev/staging/prod configuration

**Recent Session Summaries**:
- `SESSION-2026-02-14.md` - Initial setup, MyBatis-Plus integration, auth module structure
- `SESSION-2026-02-15.md` - UserService implementation, ResultCode enhancement, BusinessException optimization
- `2026-02-20` - Security fixes (CORS, file validation), Webhook module implementation

**Reference Documentation**:
- `docs/SmartATS-Design-Document.md` - Complete technical specification, database schema, API definitions
- `docs/SmartATS-从0到1开发教学手册.md` - Step-by-step development tutorial
- `docs/webhook-usage.md` - Webhook functionality guide
- `docs/security-fixes-summary.md` - Security fixes and enhancements summary
- `docs/project-progress-2026-02-20.md` - Current project progress report (NEW)
- `docs/next-development-plan.md` - Next development plan (NEW)

## Technology Stack

| Component | Technology | Version | Status |
|-----------|-----------|---------|--------|
| Core Framework | Spring Boot | 3.1.6 | ✅ |
| ORM | MyBatis-Plus | 3.5.9 | ✅ |
| Database | MySQL | 8.0 | ✅ |
| Cache | Redis | 7.0 | ✅ StringRedisTemplate |
| Distributed Lock | Redisson | - | ⏳ TODO |
| Message Queue | RabbitMQ | 3.12 | ✅ |
| File Storage | MinIO | - | ✅ |
| AI Integration | Spring AI | - | ❌ Not Started |
| Vector Database | Milvus/PgVector | - | ❌ Not Started |
| Security | Spring Security | 6.1.5 | ✅ JWT + CORS |
| Email | Jakarta Mail | - | ✅ |

## Development Environment Setup

### Required Software
1. JDK 21
2. Maven 3.9+
3. Docker Desktop
4. IntelliJ IDEA (Community Edition works)
5. Postman or Apifox (for API testing)
6. DBeaver / DataGrip (for database viewing)

### Starting Infrastructure

The project requires Docker Compose services. Use `docker-compose.yml` with:
- MySQL 8.0 (port 3307)
- Redis 7 (password: redis123)
- RabbitMQ (port 5672, management UI: 15672)
- MinIO (port 9000, console: 9001)

```bash
# Start all infrastructure services
docker-compose up -d

# Check services status
docker-compose ps
```

### Build and Run

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# Or run the JAR directly
java -jar target/smartats-*.jar
```

### Database Initialization

```bash
# Initialize database tables
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < docker/mysql/init/01-init-database.sql

# Create webhook tables
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < src/main/resources/db/webhook_tables.sql
```

## Architecture Overview

The system consists of **five main processing chains**:

1. **Authentication Chain**: Registration → Verification → Login → JWT Token → Redis Storage
2. **Upload Chain**: Upload → MD5 Hash → Deduplication Check → File Storage → DB → MQ → Async Parse
3. **Job Management Chain**: Create → Cache → Publish → View Count → Hot Ranking
4. **Webhook Chain**: Event Trigger → Async Send → Signature → Retry → Log
5. **Search Chain**: (Not Implemented) Keyword/Vector Search → RAG Retrieval

### Critical Design Patterns

**Async Processing Flow** (Resume Upload):
```
Upload → MD5 Hash → Redis Dedup Check → MinIO Storage → DB Record → Task Status (Redis) → MQ Message → Return taskId
                                                          ↓
                                      Consumer → Idempotency Check → Distributed Lock (TODO) → AI Parse (TODO) → Candidate DB → Vector Store (TODO) → Task Complete
```

**Caching Strategy**:
- Read: Check Redis first, fallback to MySQL, populate cache
- Write: Update MySQL, then delete cache key (delayed double delete for consistency)
- Hot data: Use ZSet for rankings (hot jobs)
- Atomic counters: Redis INCR + periodic sync to DB

**Rate Limiting**: Redis + Lua scripts (implementation pending)

**Distributed Locking**: Redisson with Watchdog (TODO: implementation needed)

## Module Structure

```
src/main/java/com/smartats/
├── common/                    # Shared utilities
│   ├── result/               # Result wrapper, ResultCode
│   ├── exception/            # BusinessException, GlobalExceptionHandler
│   ├── constants/            # RedisKeyConstants, etc.
│   ├── util/                 # FileValidationUtil, etc.
│   ├── annotation/           # Custom annotations
│   └── aspect/               # AOP aspects
├── config/                   # Configuration classes
│   ├── SecurityConfig.java    # Spring Security + CORS
│   ├── RabbitMQConfig.java    # Exchange, Queue, DLQ
│   ├── MinioConfig.java      # MinIO client
│   └── AsyncConfig.java      # Thread pool for @Async
├── module/                   # Business modules
│   ├── auth/                # ✅ Authentication (95%)
│   ├── job/                 # ✅ Job Management (90%)
│   ├── resume/              # ⏳ Resume Upload (80%)
│   ├── webhook/             # ⏳ Webhook (70%)
│   ├── candidate/           # ❌ Not Started
│   ├── interview/           # ❌ Not Started
│   └── ai/                  # ❌ Not Started
└── infrastructure/          # Infrastructure services
    ├── email/               # ✅ EmailService
    ├── mq/                  # ✅ MessagePublisher
    └── storage/             # ✅ MinioFileStorageService
```

## Database Schema

### Existing Tables

| Table | Status | Notes |
|-------|--------|-------|
| `users` | ✅ | User accounts with roles, AI quota |
| `jobs` | ✅ | Job postings with JSON fields, full-text index |
| `resumes` | ✅ | Resume files with MD5 deduplication |
| `candidates` | ✅ | AI-extracted structured data (empty) |
| `job_applications` | ✅ | Application tracking with match scores |
| `interview_records` | ✅ | Interview records (empty) |
| `webhook_configs` | ✅ | Webhook configuration |
| `webhook_logs` | ✅ | Webhook call logs |

### Key Relationships

- `resumes.file_hash` - Unique index for deduplication
- `candidates.resume_id` → `resumes.id` (1:1)
- `job_applications.user_id` → `users.id`
- `job_applications.job_id` → `jobs.id`
- `interview_records.application_id` → `job_applications.id`

## Redis Key Patterns

| Pattern | Type | Purpose | TTL | Implementation |
|---------|------|---------|-----|----------------|
| `jwt:token:{userId}` | String | Access Token (revocation check) | 2h | ✅ UserService |
| `jwt:refresh:{userId}` | String | Refresh Token | 7d | ✅ UserService |
| `verification_code:{email}` | String | Email verification code | 5min | ✅ VerificationCodeService |
| `verification_code_limit:{email}` | String | Rate limit for sending codes | 60s | ✅ VerificationCodeService |
| `task:resume:{taskId}` | String | Resume parsing task status | 24h | ✅ ResumeService/Consumer |
| `idempotent:resume:{resumeId}` | String | Idempotency check for MQ | 1h | ✅ ResumeParseConsumer |
| `lock:resume:{fileHash}` | String | Distributed lock for parsing | 10min | ⏳ TODO (Redisson) |
| `cache:job:{jobId}` | String | Job detail cache | 30min | ✅ JobService |
| `cache:job:hot` | ZSet | Hot job ranking | 10min | ✅ JobService |
| `counter:job:view:{jobId}` | String | Atomic view counter | - | ✅ JobService |
| `dedup:resume:{fileHash}` | String | File deduplication mark | 7d | ✅ ResumeService |

## RabbitMQ Topology

- **Exchange**: `smartats.exchange` (Direct)
- **Queue**: `resume.parse.queue`
- **DLX**: `smartats.dlx` (Dead Letter Exchange)
- **DLQ**: `resume.parse.dlq`
- **Routing Key**: `resume.parse`

**Message Flow**:
```
Producer → smartats.exchange → resume.parse.queue → Consumer
                                        ↓ (fail after retry)
                                  smartats.dlx → resume.parse.dlq
```

**Status**: ✅ Complete with JSON converter, manual ACK, retry mechanism

## API Endpoints Summary

### Authentication (✅ Complete)
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/register` | ❌ | User registration |
| POST | `/api/v1/auth/login` | ❌ | User login |
| POST | `/api/v1/auth/send-verification-code` | ❌ | Send verification code |
| GET | `/api/v1/auth/test` | ✅ | Test authentication |

### Jobs (✅ Complete)
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/jobs` | ✅ | Create job |
| PUT | `/api/v1/jobs` | ✅ | Update job |
| GET | `/api/v1/jobs/{id}` | ❌ | Get job detail |
| GET | `/api/v1/jobs` | ❌ | List jobs (paginated) |
| POST | `/api/v1/jobs/{id}/publish` | ✅ | Publish job |
| POST | `/api/v1/jobs/{id}/close` | ✅ | Close job |
| DELETE | `/api/v1/jobs/{id}` | ✅ | Delete job |
| GET | `/api/v1/jobs/hot` | ❌ | Get hot jobs |

### Resumes (⏳ Partial)
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/resumes/upload` | ✅ | Upload resume |
| GET | `/api/v1/resumes/tasks/{taskId}` | ✅ | Get task status |
| GET | `/api/v1/resumes/{id}` | ✅ | Get resume detail |
| GET | `/api/v1/resumes` | ✅ | List resumes (paginated) |

### Webhooks (⏳ Partial)
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/webhooks` | ✅ | Create webhook |
| GET | `/api/v1/webhooks` | ✅ | List webhooks |
| DELETE | `/api/v1/webhooks/{id}` | ✅ | Delete webhook |
| POST | `/api/v1/webhooks/{id}/test` | ✅ | Test webhook (TODO) |

## Development Guidelines

### ⚠️ Critical Rules

1. **Use StringRedisTemplate**, NOT `RedisTemplate<String, Object>`
   - Project standard for consistency
   - Manual JSON serialization with ObjectMapper

2. **Use LambdaQueryWrapper**, NOT string-based QueryWrapper
   - Type-safe and refactor-friendly

3. **Always check for existing implementations before creating new code**
   - Search project for similar patterns
   - Follow existing code style

4. **File uploads MUST use FileValidationUtil**
   - Content-Type validation (basic)
   - File header/magic number validation (security)
   - Filename sanitization (path traversal prevention)

5. **JWT extraction patterns** (choose one, don't mix):
   - `JwtUtil.getUserIdFromToken(request)` - Direct extraction
   - `Authentication.getPrincipal()` - Spring Security way
   - Current code uses both - needs standardization

### Code Quality Standards

**MyBatis-Plus**:
```java
// ✅ Correct
userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

// ❌ Wrong
userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
```

**Exception Handling**:
- Use `BusinessException` for business errors
- Use `ResultCode` enum for error codes
- Global handler catches all exceptions

**Redis Usage**:
```java
// ✅ Correct (project standard)
String json = objectMapper.writeValueAsString(obj);
stringRedisTemplate.opsForValue().set(key, json, ttl, TimeUnit.SECONDS);

// ❌ Wrong (not used in project)
redisTemplate.opsForValue().set(key, obj);
```

**Logging**:
- INFO: Business milestones
- WARN: Potential issues
- ERROR: System errors
- DEBUG: Detailed info (disabled in prod)

### Transaction Management

Use `@Transactional(rollbackFor = Exception.class)` for:
- Multi-step database operations
- Operations that must be atomic
- Any method modifying multiple tables

## Known Issues and TODOs

### Critical (Must Fix)
1. **Compilation Error**: `@URL` annotation not found
   - Fix: Add jakarta.validation-api dependency to pom.xml

2. **AI Parsing Not Implemented**: ResumeParseConsumer uses mock
   - Impact: Candidates cannot be extracted from resumes
   - Location: `ResumeParseConsumer.java:81-87`

3. **Distributed Lock Not Implemented**: Using TODO comment
   - Impact: Race condition risk in concurrent parsing
   - Location: `ResumeParseConsumer.java:66`

### Medium Priority
4. **Webhook Test Function**: Endpoint exists but not implemented
   - Location: `WebhookController.java:86`

5. **JWT Extraction Inconsistency**: Two different patterns used
   - Need to standardize on one approach

6. **Configuration Security**: Sensitive data hardcoded in application.yml
   - JWT secret, DB passwords, Redis password, etc.
   - Should use environment variables

### Low Priority
7. **Retry Mechanism**: Message retry logic is simplified
   - Location: `ResumeParseConsumer.java:188`
   - Should implement proper retry with backoff

8. **Test Coverage**: No unit tests for critical business logic

9. **API Documentation**: Swagger/OpenAPI not configured

## Testing

### Manual Testing Checklist

Before marking a feature complete:
- [ ] API returns correct response codes
- [ ] Error scenarios return appropriate messages
- [ ] Database transactions handled correctly
- [ ] Sensitive data not exposed
- [ ] Logs written at appropriate levels
- [ ] Code follows project style

### Test with Webhook.site

For testing webhooks without a real server:
1. Visit https://webhook.site
2. Copy the provided URL
3. Create a webhook configuration with that URL
4. Trigger an event (e.g., upload a resume)
5. View received requests on webhook.site

## Git Workflow

### Commit Message Format

```
type(scope): subject

body

footer
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `refactor`: Code refactoring
- `perf`: Performance improvement
- `security`: Security fix
- `test`: Test additions
- `chore`: Build/config changes

Examples:
- `feat(auth): add refresh token support`
- `fix(resume): correct file deduplication logic`
- `security(webhook): add signature verification`
- `docs(api): update authentication guide`

### Branch Strategy

- `main` - Production code
- `develop` - Development branch
- `feature/*` - Feature branches
- `fix/*` - Bug fix branches
