# SmartATS 下一步开发计划

**制定日期**: 2026-02-23  
**当前进度**: ~92%（核心业务逻辑已完成）

---

## 已完成回顾

| 阶段 | 内容 | 状态 |
|------|------|------|
| 基础框架 | Spring Boot 3.2.5 + MyBatis-Plus + Security + Redis + RabbitMQ + MinIO | ✅ |
| 认证模块 | 注册/登录/JWT/验证码/Token 刷新 | ✅ |
| 职位管理 | CRUD + 缓存 + 热榜 + 浏览计数同步 | ✅ |
| 简历上传 | 上传/去重/MQ 异步/AI 解析/候选人提取 | ✅ |
| 候选人模块 | CRUD + 高级筛选 + 缓存 + 脱敏 | ✅ |
| 职位申请 | 创建/状态变更/多维查询 + N+1 修复 | ✅ |
| 面试记录 | 安排/反馈/取消/查询 + N+1 修复 | ✅ |
| Webhook | 创建/删除/测试/13 种事件/签名验证 | ✅ |
| 代码优化 | 34 项质量问题修复（安全/性能/架构/异常） | ✅ |

---

## 下一步优先级排序

### 🔴 Step 1：补充单元测试（最高优先级）

**原因**：当前测试覆盖率约 0%，仅有 1 个 MinIO 集成测试。核心业务逻辑完全无测试保障。

**计划**：

| 模块 | 测试目标 | 重点 |
|------|---------|------|
| auth | UserService | 注册/登录/Token 刷新逻辑 |
| job | JobService | CRUD + 缓存策略 + 热榜 |
| resume | ResumeService | 上传/去重/MQ 发送 |
| resume | ResumeParseConsumer | 消费/幂等/重试流程 |
| candidate | CandidateService | 高级筛选/缓存/更新 |
| application | JobApplicationService | 创建/状态流转/防重复 |
| interview | InterviewService | 安排/反馈/取消 |

**技术方案**：
- JUnit 5 + Mockito + Spring Boot Test
- Service 层：@ExtendWith(MockitoExtension.class)，Mock Mapper/Redis
- Controller 层：@WebMvcTest + MockMvc
- 目标覆盖率：60%+

**预计工作量**: 1-2 周

---

### 🟡 Step 2：简历批量上传

**原因**：HR 实际场景中需要批量导入，单文件上传效率太低。

**需实现**：
- `POST /api/v1/resumes/batch-upload` — 支持最多 20 个文件
- 每个文件独立走 MD5 去重 + MQ 异步解析
- 整体响应：总数、成功数、重复数、各文件 taskId
- 限流：Redis + Lua（每分钟最多 3 次批量请求）

**预计工作量**: 1-2 天

---

### 🟡 Step 3：Swagger / OpenAPI 接入

**原因**：37 个 API 端点无在线文档，不利于前端对接和调试。

**技术方案**：SpringDoc OpenAPI 3

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

**配置要点**：
- 访问地址：`http://localhost:8080/swagger-ui.html`
- JWT Bearer Token 输入框
- SecurityConfig 放行 `/swagger-ui/**` 和 `/v3/api-docs/**`
- 关键接口加 `@Operation` + `@Parameter` 注解

**预计工作量**: 0.5 天

---

### 🟡 Step 4：向量搜索（RAG 语义搜索）

**原因**：项目差异化亮点，AI 语义搜索依赖前序模块数据积累。

**技术选型建议**：

| 方案 | 优点 | 缺点 | 推荐场景 |
|------|------|------|---------|
| Redis Vector | 已有 Redis，零成本扩展 | 功能有限 | MVP 快速验证 |
| PgVector | 无需额外组件 | 需迁移数据库 | 轻量场景 |
| Milvus | 专业向量库，高性能 | 需额外 Docker 服务 | 大规模数据 |

**建议先用 Redis Vector 快速验证**，数据量大后再迁移 Milvus。

**实现步骤**：
1. 候选人保存后，调用 Spring AI `EmbeddingModel` 生成向量
2. 存入向量存储（Redis/Milvus）
3. 搜索时对 query 生成向量，cosine similarity 检索 Top-K
4. 与关键字搜索结果融合（RRF 算法）

**预计工作量**: 1 周

---

### 🟢 Step 5：工程化完善

**内容**：
- 环境分离配置（dev/staging/prod profiles）
- CORS 生产域名配置（替换 `*`）
- 敏感配置移至环境变量
- Docker 多阶段构建部署
- 监控日志完善

**预计工作量**: 1-2 天

---

## 开发顺序

```
现在 ──────────────────────────────────────────────── 未来
  │
  ├─ Step 1: 单元测试（1-2 周）
  │
  ├─ Step 2: 批量上传（1-2 天）
  │
  ├─ Step 3: Swagger 文档（0.5 天）— 可穿插
  │
  ├─ Step 4: 向量检索 / RAG（1 周）
  │
  └─ Step 5: 工程化完善（1-2 天）
```

**总预计时间**: 3-4 周

---

## 技术决策备忘

### 已确定的设计原则
- Redis 缓存：cache-aside 模式，写后删除（延迟双删）
- 消息队列：Direct Exchange + DLQ，手动 ACK，republish 重试
- 分布式锁：Redisson Watchdog 模式
- ORM：LambdaQueryWrapper（禁止 string-based QueryWrapper）
- Redis 客户端：StringRedisTemplate + ObjectMapper
- 异常处理：统一 BusinessException + ResultCode
- JWT 提取：`Authentication.getPrincipal()` 方式

### 待决策

| 问题 | 选项 | 建议 |
|------|------|------|
| 向量数据库 | Redis Vector / PgVector / Milvus | 先 Redis Vector |
| 前端技术 | 纯 API / 简单前端 | 纯 API + Swagger |
| 部署方式 | Docker Compose / K8s | 当前 Docker Compose 足够 |

---

*文档更新时间: 2026-02-23*
