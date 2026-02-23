# SmartATS 下一步开发计划

**制定日期**: 2026-02-21  
**当前进度**: ~85%（Step 1 + Step 2 已完成）  
**作者**: AI 辅助规划

---

## 已完成回顾

| 阶段 | 内容 | 状态 |
|------|------|------|
| Step 1 | Webhook 测试接口、RefreshToken 刷新接口、SecurityConfig 白名单 | ✅ |
| Step 2 | 候选人模块：Redis 缓存、高级多维筛选、手机/邮箱脱敏 | ✅ |

---

## 下一步优先级排序

### 🔴 Step 3：职位申请模块（job_applications）⭐ 最高优先级

**原因**：`job_applications` 表已存在，却没有任何 Java 代码。它是整个招聘流程的核心枢纽——连接候选人与职位，是面试模块的前置依赖。

**需实现的功能**：

#### 3.1 实体与 Mapper
- `JobApplication.java` 实体（映射 `job_applications` 表）
- `JobApplicationMapper.java`

#### 3.2 Service 层
```
创建申请  createApplication(candidateId, jobId, userId)
           → 校验候选人/职位是否存在
           → 检查是否已申请（防重复）
           → 初始 status = PENDING
           → 触发 Webhook（RESUME_APPLIED 事件，需新增）

更新状态  updateStatus(id, status)
           → PENDING → REVIEWING → INTERVIEW → OFFER → REJECTED

查询申请  listByJobId(jobId, page, pageSize)   ← HR视角：某职位的所有申请
          listByCandidateId(candidateId, ...)  ← 候选人视角
          getById(id)
```

#### 3.3 Controller 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/applications` | 创建申请 |
| PUT | `/api/v1/applications/{id}/status` | 更新申请状态 |
| GET | `/api/v1/applications/{id}` | 获取申请详情 |
| GET | `/api/v1/applications/job/{jobId}` | 某职位的申请列表 |
| GET | `/api/v1/applications/candidate/{candidateId}` | 某候选人的申请列表 |

#### 3.4 状态流转
```
PENDING（待处理）
  → REVIEWING（简历筛选中）
  → INTERVIEW（进入面试）→ 触发创建面试记录
  → OFFER（发放 Offer）
  → REJECTED（淘汰）
```

**预计工作量**: 2-3 天

---

### 🟡 Step 4：面试记录模块（interview）

**原因**：`interview_records` 表已存在，需要 `job_applications` 才能关联，放在 Step 3 之后。

**需实现的功能**：

#### 4.1 实体与 Mapper
- `InterviewRecord.java` 实体
- `InterviewRecordMapper.java`

#### 4.2 Service 层
```
安排面试    scheduleInterview(applicationId, round, scheduledAt, interviewers)
提交反馈    submitFeedback(id, score, evaluation, recommendation)
             → recommendation: STRONG_YES / YES / NO / STRONG_NO
查询面试    listByApplicationId(applicationId)
            getById(id)
```

#### 4.3 Controller 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/interviews` | 安排面试 |
| PUT | `/api/v1/interviews/{id}/feedback` | 提交面试反馈 |
| GET | `/api/v1/interviews/{id}` | 获取面试详情 |
| GET | `/api/v1/interviews/application/{appId}` | 某申请的所有面试轮次 |

**预计工作量**: 2-3 天

---

### 🟡 Step 5：简历批量上传

**原因**：HR 实际场景中需要批量导入，单文件上传效率太低。

**需实现的功能**：
- `POST /api/v1/resumes/batch-upload` — 支持最多 20 个文件，返回每个文件的 taskId
- 每个文件独立走 MD5 去重 + MQ 异步解析流程
- 整体响应格式：
```json
{
  "total": 5,
  "success": 4,
  "duplicates": 1,
  "tasks": [
    { "filename": "xxx.pdf", "taskId": "uuid", "status": "processing" },
    { "filename": "yyy.pdf", "taskId": null, "status": "duplicate" }
  ]
}
```
- 限流：同一用户批量上传 `@RateLimiter`（Redis + Lua，建议每分钟最多 3 次批量请求）

**预计工作量**: 1-2 天

---

### 🟢 Step 6：Swagger / OpenAPI 接入

**原因**：目前没有 API 文档，Postman 调试效率低，也不利于前端对接。

**技术方案**：SpringDoc OpenAPI 3（与 Spring Boot 3 兼容更好，勿用旧版 Springfox）

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

**配置要点**：
- 访问地址：`http://localhost:8080/swagger-ui.html`
- JWT 认证支持（Bearer Token 输入框）
- SecurityConfig 放行 `/swagger-ui/**` 和 `/v3/api-docs/**`
- 关键接口加 `@Operation` + `@Parameter` 注解

**预计工作量**: 0.5 天

---

### 🔵 Step 7：向量搜索（RAG 语义搜索）— 后期

**原因**：依赖 Step 2-4 数据积累，AI 搜索是项目的差异化亮点，但实现复杂度最高。

**技术选型建议**：

| 方案 | 优点 | 缺点 | 推荐场景 |
|------|------|------|---------|
| **PgVector** | 依赖 PostgreSQL，无需额外组件 | 需迁移数据库 | 轻量场景 |
| **Milvus** | 专业向量库，高性能 | 需额外 Docker 服务 | 大规模数据 |
| **Redis Vector** | 已有 Redis，零成本扩展 | 功能有限 | MVP 快速验证 |

**建议先用 Redis Vector（HNSWLib）快速验证流程**，数据量大后再迁移 Milvus。

**实现步骤**：
1. 候选人保存后，调用 Spring AI `EmbeddingModel` 生成 512 维向量
2. 存入向量存储（Redis/Milvus）
3. 搜索时，对 query 生成向量，做 cosine similarity 检索 Top-K
4. 与关键字搜索结果融合（RRF 算法）

**预计工作量**: 1 周

---

## 开发顺序建议

```
现在 ──────────────────────────────────────────────── 未来
  │
  ├─ Step 3: 职位申请模块（2-3天）
  │
  ├─ Step 4: 面试记录模块（2-3天）
  │
  ├─ Step 5: 批量上传（1-2天）
  │
  ├─ Step 6: Swagger 文档（0.5天）- 可穿插完成
  │
  └─ Step 7: 向量检索（1周）
```

**总预计时间**: 2-3 周

---

## 技术决策备忘

### 已确定的设计原则
- Redis 缓存：cache-aside 模式，写后失效（不用写穿）
- 消息队列：Direct Exchange + DLQ，手动 ACK
- 分布式锁：Redisson Watchdog 模式
- ORM：LambdaQueryWrapper（禁止 string-based QueryWrapper）
- Redis 客户端：StringRedisTemplate + ObjectMapper（禁止 RedisTemplate<String, Object>）

### 待决策
| 问题 | 选项 | 建议 |
|------|------|------|
| 向量数据库 | PgVector / Milvus / Redis | 先 Redis Vector，后期升级 Milvus |
| 前端技术 | 纯 API / 简单前端 | 纯 API + Swagger，前端后续另立项目 |
| 部署方式 | Docker Compose / K8s | 当前 Docker Compose 足够 |

---

## 当前已知问题（需在开发中修复）

| 优先级 | 问题 | 建议修复时机 |
|--------|------|-------------|
| 🟡 中 | JWT 提取方式不统一（两种方式混用） | Step 3 之前统一 |
| 🟡 中 | 硬编码配置未移至环境变量 | Step 6 工程化时一并处理 |
| 🟢 低 | WebhookEventType 缺少 `APPLICATION_CREATED` 等新事件 | Step 3 时新增 |

---

*文档更新时间: 2026-02-21*
