# SmartATS 下一步开发计划

**制定日期**: 2026-02-20
**计划周期**: 2026-02-21 ~ 2026-03-15 (4周)
**当前版本**: 0.8.0
**目标版本**: 1.0.0

---

## 一、总体目标

完成核心 AI 功能和候选人管理模块，使产品达到 MVP (最小可行产品) 状态，支持完整的简历上传 → AI 解析 → 候选人管理 → 职位申请流程。

---

## 二、关键里程碑

| 里程碑 | 目标日期 | 交付物 |
|--------|---------|--------|
| M1: 修复编译错误 | D+1 | 项目可正常编译运行 |
| M2: AI 解析功能 | D+5 | 简历可自动提取结构化数据 |
| M3: 候选人模块 | D+10 | 候选人 CRUD 和搜索功能 |
| M4: 单元测试 | D+15 | 核心功能测试覆盖 60%+ |

---

## 三、详细开发计划

### 第一阶段：问题修复 (1天)

**优先级**: P0 (必须完成)

#### 任务 1.1: 修复编译错误
- [ ] 在 `pom.xml` 添加 `jakarta.validation-api` 依赖
- [ ] 验证 WebhookCreateRequest 编译通过
- [ ] 运行 `mvn clean install` 确认无编译错误

**预计时间**: 30分钟

#### 任务 1.2: 集成 Redisson 分布式锁
- [ ] 在 `pom.xml` 添加 `redisson-spring-boot-starter` 依赖
- [ ] 创建 `RedissonConfig.java` 配置类
- [ ] 在 `ResumeParseConsumer` 中使用分布式锁
- [ ] 测试并发场景

**文件**:
```
config/RedissonConfig.java (新建)
module/resume/consumer/ResumeParseConsumer.java (修改)
```

**预计时间**: 2小时

---

### 第二阶段：AI 简历解析 (5天)

**优先级**: P0 (核心功能)

#### 任务 2.1: 集成 Spring AI
- [ ] 在 `pom.xml` 添加 Spring AI 依赖
- [ ] 配置 AI 服务 (OpenAI/Azure/Aliyun)
- [ ] 创建 AI 配置类 `AIConfig.java`
- [ ] 实现 PDF 文件读取
- [ ] 实现 Word 文件读取

**依赖**:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
</dependency>
```

**预计时间**: 1天

#### 任务 2.2: 实现 AI 简历解析
- [ ] 创建 `ResumeParserService.java`
- [ ] 设计 Prompt 模板
- [ ] 实现结构化数据提取
- [ ] 处理 AI 解析失败重试
- [ ] 添加解析日志

**提取字段**:
- 基本信息: 姓名、性别、出生年月、手机、邮箱
- 教育经历: 学校、专业、学历、毕业时间
- 工作经历: 公司、职位、时间、描述
- 技能标签: 编程语言、框架、工具等

**预计时间**: 2天

#### 任务 2.3: 保存候选人数据
- [ ] 在 `ResumeParseConsumer` 中调用 AI 解析
- [ ] 创建/更新 `candidates` 表记录
- [ ] 实现数据验证和清洗
- [ ] 处理解析异常情况

**预计时间**: 1天

#### 任务 2.4: 测试和优化
- [ ] 准备多种格式测试简历 (PDF/Word)
- [ ] 测试边界情况 (格式错误、信息缺失)
- [ ] 优化 Prompt 提高准确率
- [ ] 添加解析失败告警

**预计时间**: 1天

---

### 第三阶段：候选人模块 (3天)

**优先级**: P1 (核心功能)

#### 任务 3.1: 候选人 CRUD
- [ ] 创建 `CandidateController.java`
- [ ] 创建 `CandidateService.java`
- [ ] 实现分页查询
- [ ] 实现详情查询
- [ ] 实现候选人更新
- [ ] 实现候选人删除

**API**:
```
GET    /api/v1/candidates              # 列表 (分页)
GET    /api/v1/candidates/{id}         # 详情
PUT    /api/v1/candidates/{id}         # 更新
DELETE /api/v1/candidates/{id}         # 删除
```

**预计时间**: 1.5天

#### 任务 3.2: 候选人搜索
- [ ] 实现关键词搜索
- [ ] 实现高级筛选 (学历、经验、技能)
- [ ] 集成 Redis 缓存热门搜索
- [ ] 实现搜索历史记录

**API**:
```
GET /api/v1/candidates/search?q=Java&education=本科&experience=3
```

**预计时间**: 1天

#### 任务 3.3: 简历预览
- [ ] 实现简历文件在线预览 (PDF.js)
- [ ] 实现简历下载
- [ ] 添加水印保护

**预计时间**: 0.5天

---

### 第四阶段：职位申请模块 (2天)

**优先级**: P1 (核心业务流程)

#### 任务 4.1: 职位申请 CRUD
- [ ] 创建 `JobApplicationController.java`
- [ ] 创建 `JobApplicationService.java`
- [ ] 实现申请提交
- [ ] 实现申请状态流转
- [ ] 实现申请列表查询

**API**:
```
POST   /api/v1/jobs/{jobId}/apply                    # 提交申请
GET    /api/v1/applications                          # 申请列表
GET    /api/v1/applications/{id}                     # 申请详情
PUT    /api/v1/applications/{id}/status             # 更新状态
```

**预计时间**: 1.5天

#### 任务 4.2: 匹配度计算
- [ ] 实现关键词匹配算法
- [ ] 实现技能匹配评分
- [ ] 实现经验匹配评分
- [ ] 综合评分计算

**预计时间**: 0.5天

---

### 第五阶段：测试和优化 (3天)

**优先级**: P1 (质量保障)

#### 任务 5.1: 单元测试
- [ ] UserService 测试
- [ ] JobService 测试
- [ ] ResumeService 测试
- [ ] CandidateService 测试
- [ ] AI 解析测试 (Mock)

**目标覆盖率**: 60%+

**预计时间**: 1.5天

#### 任务 5.2: 集成测试
- [ ] 简历上传 → AI 解析 → 候选人创建 流程
- [ ] 职位申请 → 状态流转 流程
- [ ] Webhook 事件触发测试

**预计时间**: 0.5天

#### 任务 5.3: 性能优化
- [ ] 数据库查询优化 (添加索引)
- [ ] Redis 缓存策略优化
- [ ] AI 调用批量处理
- [ ] 文件上传并发优化

**预计时间**: 0.5天

#### 任务 5.4: 安全加固
- [ ] 敏感配置迁移到环境变量
- [ ] 添加 API 限流 (Redis + Lua)
- [ ] 完善错误处理 (避免信息泄露)
- [ ] 添加操作审计日志

**预计时间**: 0.5天

#### 任务 5.5: 文档完善
- [ ] API 文档 (Swagger/OpenAPI)
- [ ] 部署文档
- [ ] 运维手册
- [ ] 故障排查指南

**预计时间**: 0.5天

---

### 第六阶段：面试模块 (可选，2天)

**优先级**: P2 (后续功能)

#### 任务 6.1: 面试安排
- [ ] 创建 `InterviewController.java`
- [ ] 创建 `InterviewService.java`
- [ ] 实现面试创建
- [ ] 实现面试状态管理
- [ ] 实现面试提醒 (Webhook + 邮件)

**API**:
```
POST /api/v1/applications/{id}/interviews       # 创建面试
PUT  /api/v1/interviews/{id}                    # 更新面试
GET  /api/v1/interviews                         # 面试列表
```

**预计时间**: 1天

#### 任务 6.2: 面试反馈
- [ ] 实现面试评价表单
- [ ] 实现面试记录查询
- [ ] 实现面试统计

**预计时间**: 1天

---

## 四、技术债务清理

### 4.1 代码重构
- [ ] 统一 JWT 提取方式 (当前有两种)
- [ ] 抽取公共代码逻辑
- [ ] 优化异常处理链

### 4.2 配置优化
- [ ] 环境配置分离 (dev/staging/prod)
- [ ] 敏感信息迁移到环境变量
- [ ] 配置中心集成 (Spring Cloud Config / Apollo)

### 4.3 监控和日志
- [ ] 集成 Prometheus + Grafana
- [ ] 集成 ELK (日志分析)
- [ ] 添加分布式链路追踪 (SkyWalking / Zipkin)

---

## 五、依赖添加计划

### pom.xml 需要添加的依赖

```xml
<!-- 验证注解 (修复编译错误) -->
<dependency>
    <groupId>org.glassfish</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>

<!-- Redisson 分布式锁 -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.24.3</version>
</dependency>

<!-- Spring AI (OpenAI) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>0.8.1</version>
</dependency>

<!-- PDF 解析 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>2.0.29</version>
</dependency>

<!-- Word 解析 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>

<!-- 单元测试 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- API 文档 -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>
```

---

## 六、数据库变更

### 需要执行的 SQL

```sql
-- webhook 表 (待执行)
source src/main/resources/db/webhook_tables.sql;

-- 优化索引
ALTER TABLE candidates ADD INDEX idx_user_id (user_id);
ALTER TABLE candidates ADD INDEX idx_skills ((CAST(skills->'$.programming_languages' AS CHAR(255))));

-- 全文索引 (如果尚未创建)
ALTER TABLE candidates ADD FULLTEXT INDEX ft_content (name, email, phone);
```

---

## 七、测试计划

### 7.1 单元测试覆盖目标

| 模块 | 覆盖率目标 | 优先级 |
|------|-----------|--------|
| UserService | 80% | P0 |
| JobService | 70% | P0 |
| ResumeService | 70% | P0 |
| CandidateService | 60% | P1 |
| WebhookService | 50% | P2 |

### 7.2 集成测试场景

1. **用户注册 → 登录 → 上传简历 → AI 解析 → 查看候选人**
2. **创建职位 → 发布 → 候选人申请 → 状态流转**
3. **Webhook 事件触发 → 签名验证 → 接收处理**

---

## 八、发布计划

### 8.1 版本规划

| 版本 | 日期 | 主要功能 |
|------|------|---------|
| v0.9.0 | D+5 | AI 简历解析 |
| v0.9.5 | D+10 | 候选人模块 |
| v1.0.0 | D+15 | 职位申请 + 测试完成 |
| v1.1.0 | D+22 | 面试模块 (可选) |

### 8.2 发布检查清单

- [ ] 所有 P0 任务完成
- [ ] 单元测试覆盖率达标
- [ ] 集成测试通过
- [ ] 性能测试通过
- [ ] 安全扫描无高危漏洞
- [ ] 文档更新完整
- [ ] 生产环境配置就绪

---

## 九、资源分配

### 9.1 人力资源

假设 1 名全职开发：

| 阶段 | 工作日 | 累计 |
|------|--------|------|
| 问题修复 | 1 | 1 |
| AI 解析 | 5 | 6 |
| 候选人模块 | 3 | 9 |
| 职位申请 | 2 | 11 |
| 测试优化 | 3 | 14 |
| 面试模块 | 2 (可选) | 16 |

### 9.2 基础设施资源

- OpenAI API 额度 (或其他 LLM 服务)
- 向量数据库 (Milvus / PgVector)
- 监控系统 (Prometheus + Grafana)

---

## 十、风险管理

### 10.1 技术风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| AI 解析准确率不达标 | 高 | 多轮 Prompt 优化、备用方案 |
| 第三方 API 限流 | 中 | 实现重试机制、多服务商支持 |
| 性能瓶颈 | 中 | 缓存优化、异步处理 |
| 数据丢失 | 高 | 定期备份、主从复制 |

### 10.2 进度风险

| 风险 | 概率 | 应对 |
|------|------|------|
| AI 集成复杂度超预期 | 中 | 延后面试模块、优先 MVP |
| 测试时间不足 | 高 | 并行开发、自动化测试 |
| 需求变更 | 中 | 敏捷迭代、小步快跑 |

---

## 十一、下一步行动 (明天)

### 立即执行 (优先级 P0)

1. **添加 jakarta.validation 依赖** (10分钟)
2. **添加 Redisson 依赖并配置** (30分钟)
3. **在 ResumeParseConsumer 中集成分布式锁** (1小时)
4. **测试并发场景** (30分钟)

### 本周目标

- [ ] 项目可正常编译运行
- [ ] 分布式锁正常工作
- [ ] Spring AI 集成完成
- [ ] 完成第一个 AI 解析测试

---

**计划制定人**: Claude
**审核**: 待定
**批准**: 待定
