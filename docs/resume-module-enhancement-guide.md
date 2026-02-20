# SmartATS 简历模块完善开发手册

**文档版本**: v1.0
**创建日期**: 2026-02-20
**目标**: 完善简历模块的 AI 解析和候选人管理功能

---

## 📋 目录

1. [当前状态分析](#当前状态分析)
2. [待完善功能清单](#待完善功能清单)
3. [技术方案设计](#技术方案设计)
4. [实现步骤详解](#实现步骤详解)
5. [完整代码示例](#完整代码示例)
6. [为什么这样设计](#为什么这样设计)
7. [测试验证](#测试验证)

---

## 当前状态分析

### ✅ 已完成的功能

#### 1. 简历上传流程

**文件**: `ResumeService.java`

```java
public ResumeUploadResponse uploadResume(MultipartFile file, Long userId)
```

**功能点**：
- 文件校验（类型、大小、魔数验证）
- MD5 哈希计算用于去重
- 双重去重检查（Redis + 数据库）
- MinIO 文件存储
- 数据库记录保存
- RabbitMQ 消息发送
- 任务状态初始化（Redis）

**为什么这样设计**：
- **MD5 去重**：节省存储空间，避免重复解析
- **双重检查**：Redis 快速查询 + 数据库持久化，保证准确性
- **异步处理**：用户无需等待 AI 解析完成，立即返回 taskId

#### 2. 任务状态查询

**文件**: `ResumeController.java`

```java
@GetMapping("/tasks/{taskId}")
public Result<TaskStatusResponse> getTaskStatus(@PathVariable String taskId)
```

**功能点**：
- 从 Redis 查询任务状态
- 返回解析进度百分比
- 包含错误信息（如有）

**为什么这样设计**：
- **Redis 缓存**：高速读取，不频繁查询数据库
- **24小时 TTL**：自动清理过期任务数据

#### 3. 消息消费者

**文件**: `ResumeParseConsumer.java`

```java
@RabbitListener(queues = RabbitMQConfig.RESUME_PARSE_QUEUE)
public void consumeResumeParse(ResumeParseMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
```

**功能点**：
- 幂等性检查（防止重复处理）
- 手动 ACK 消息确认
- 失败重试机制（最多3次）
- Webhook 事件触发
- 模拟 AI 解析（Thread.sleep）

**当前问题**：
- ⚠️ AI 解析是模拟的，需要实际实现
- ⚠️ 分布式锁未实现（只有 TODO 注释）
- ⚠️ 解析结果未存储到 candidates 表

---

## 待完善功能清单

### 🔴 高优先级（核心功能）

#### 1. AI 简历解析服务

**需求**：
- 从 MinIO 下载简历文件
- 使用 Spring AI 调用 LLM 解析内容
- 提取结构化候选人信息
- 存储到 candidates 表

**涉及文件**：
- `ResumeParseService.java` (新建)
- `SpringAIConfig.java` (新建)
- `CandidateService.java` (新建)

#### 2. 候选人管理模块

**需求**：
- 创建 candidates 表
- 创建 Candidate 实体和 Mapper
- 实现 CRUD 接口
- 与简历表的 1:1 关系

**涉及文件**：
- `Candidate.java` (新建)
- `CandidateMapper.java` (新建)
- `CandidateService.java` (新建)
- `CandidateController.java` (新建)
- `candidates_table.sql` (新建)

#### 3. Redisson 分布式锁

**需求**：
- 替换现有的简单锁注释
- 防止同一文件被并发解析
- 使用看门狗机制自动续期

**涉及文件**：
- `RedissonConfig.java` (新建)
- `ResumeParseConsumer.java` (修改)

### 🟡 中优先级（增强功能）

#### 4. 简历列表查询

**需求**：
- 分页查询用户的简历列表
- 支持按状态、日期筛选
- 关联候选人信息

**涉及文件**：
- `ResumeController.java` (修改)
- `ResumeService.java` (修改)

#### 5. 简历详情查询

**需求**：
- 查询单个简历详情
- 返回关联的候选人结构化信息
- 返回 AI 解析的原始 JSON

**涉及文件**：
- `ResumeController.java` (修改)
- `ResumeDetailResponse.java` (新建 DTO)

#### 6. 简历删除功能

**需求**：
- 删除简历记录
- 删除 MinIO 文件
- 删除关联的候选人记录
- 清理 Redis 缓存

**涉及文件**：
- `ResumeController.java` (修改)
- `ResumeService.java` (修改)

### 🟢 低优先级（优化功能）

#### 7. 批量上传

**需求**：
- 支持一次上传多个简历文件
- 返回多个 taskId
- 进度分别跟踪

#### 8. 简历编辑

**需求**：
- 允许手动编辑 AI 解析结果
- 修正提取错误的字段

---

## 技术方案设计

### 1. AI 解析方案

#### 技术选型：Spring AI + OpenAI API

**为什么选择 Spring AI**：
- Spring 官方生态，集成简单
- 支持多种 LLM 提供商（OpenAI、Azure、通义千问等）
- 提供结构化输出支持（JSON Schema）
- 自动重试和错误处理

#### Prompt 设计

```java
String prompt = """
你是一个专业的简历信息提取助手。请从以下简历内容中提取结构化信息，并以 JSON 格式返回。

提取字段：
1. name: 姓名
2. phone: 手机号
3. email: 邮箱
4. gender: 性别（男/女）
5. age: 年龄
6. education: 教育程度（本科/硕士/博士等）
7. school: 毕业院校
8. major: 专业
9. workYears: 工作年限（年）
10. currentCompany: 当前公司
11. currentPosition: 当前职位
12. skills: 技能列表（字符串数组）
13. workExperience: 工作经历（JSON 数组，包含公司、职位、时间、职责）
14. projectExperience: 项目经历（JSON 数组，包含项目名称、角色、时间、描述）
15. selfEvaluation: 自我评价

注意事项：
- 如果某个字段无法提取，使用 null
- 日期格式统一为 yyyy-MM-dd
- 技能列表提取关键词，如 Java、Spring、MySQL 等
- 工作经历和项目经历按时间倒序排列

简历内容：
{resume_content}
""";
```

#### 结构化输出

使用 Spring AI 的 `BeanOutputConverter` 确保返回符合格式的 JSON：

```java
BeanOutputConverter<CandidateInfo> converter =
    new BeanOutputConverter<>(CandidateInfo.class);
```

### 2. 候选人表设计

```sql
CREATE TABLE `candidates` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    -- 关联简历
    `resume_id` BIGINT NOT NULL UNIQUE COMMENT '简历ID（1:1关系）',

    -- 基本信息
    `name` VARCHAR(100) COMMENT '姓名',
    `phone` VARCHAR(20) COMMENT '手机号',
    `email` VARCHAR(100) COMMENT '邮箱',
    `gender` VARCHAR(10) COMMENT '性别',
    `age` INT COMMENT '年龄',

    -- 教育信息
    `education` VARCHAR(50) COMMENT '学历（本科/硕士/博士）',
    `school` VARCHAR(200) COMMENT '毕业院校',
    `major` VARCHAR(200) COMMENT '专业',
    `graduation_year` INT COMMENT '毕业年份',

    -- 工作信息
    `work_years` INT COMMENT '工作年限',
    `current_company` VARCHAR(200) COMMENT '当前公司',
    `current_position` VARCHAR(200) COMMENT '当前职位',

    -- JSON 字段（复杂结构）
    `skills` JSON COMMENT '技能列表 ["Java", "Spring", "MySQL"]',
    `work_experience` JSON COMMENT '工作经历 [{company, position, startDate, endDate, description}]',
    `project_experience` JSON COMMENT '项目经历 [{name, role, startDate, endDate, description}]',
    `self_evaluation` TEXT COMMENT '自我评价',

    -- AI 解析元数据
    `raw_json` TEXT COMMENT 'AI 解析的原始 JSON 结果',
    `confidence_score` DECIMAL(3,2) COMMENT '置信度分数 (0.00-1.00)',
    `parsed_at` DATETIME COMMENT '解析时间',

    -- 审计字段
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_resume_id (resume_id),
    INDEX idx_name (name),
    INDEX idx_phone (phone),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候选人信息表';
```

**为什么使用 JSON 字段**：
- **灵活性**：工作经历、项目经历结构复杂，字段数量不固定
- **可扩展**：新增字段不需要修改表结构
- **查询能力**：MySQL 5.7+ 支持 JSON 索引和查询

### 3. Redisson 分布式锁方案

```java
RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + fileHash);

try {
    // 尝试获取锁，最多等待 10 秒，锁自动释放时间 30 秒
    boolean acquired = lock.tryLock(10, 300, TimeUnit.SECONDS);

    if (!acquired) {
        log.warn("获取锁失败，文件正在被其他实例处理: fileHash={}", fileHash);
        channel.basicAck(deliveryTag, false);
        return;
    }

    // 执行解析逻辑
    ...

} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

**为什么使用 Redisson**：
- **看门狗机制**：自动续期，防止业务未执行完锁就释放
- **可重入锁**：同一线程可多次获取锁
- **公平锁**：支持先来先得（可选）
- **红锁**：支持多主节点高可用（可选）

### 4. 架构流程图

```
┌─────────────┐
│   用户上传   │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────┐
│  ResumeController           │
│  - JWT 认证获取 userId       │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│  ResumeService.uploadResume │
│  - 文件校验                  │
│  - MD5 去重检查              │
│  - MinIO 上传                │
│  - 保存 resumes 表           │
│  - 发送 MQ 消息              │
└──────┬──────────────────────┘
       │
       ▼                    ┌──────────────┐
┌──────────────────┐        │  RabbitMQ    │
│  返回 taskId     │        │  队列        │
└──────────────────┘        └──────┬───────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────┐
│  ResumeParseConsumer                         │
│  - 幂等性检查                                │
│  - Redisson 分布式锁                         │
│  - 更新状态 PROCESSING                       │
└──────┬───────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────┐
│  ResumeParseService                         │
│  - 从 MinIO 下载文件                         │
│  - Spring AI 解析内容                        │
│  - 提取结构化信息                            │
└──────┬───────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────┐
│  CandidateService                           │
│  - 保存 candidates 表                        │
│  - 更新 resumes.status = COMPLETED          │
└──────┬───────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────┐
│  更新 Redis 任务状态                         │
│  - status = COMPLETED                        │
│  - progress = 100                            │
└──────┬───────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────┐
│  WebhookService                             │
│  - 触发 RESUME_PARSE_COMPLETED 事件          │
└──────────────────────────────────────────────┘
```

---

## 实现步骤详解

### 阶段一：基础设施准备（1-2天）

#### 步骤 1.1：添加 Maven 依赖

在 `pom.xml` 中添加：

```xml
<!-- Spring AI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M4</version>
</dependency>

<!-- Redisson -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.25.0</version>
</dependency>

<!-- Apache POI（用于解析 DOC 文件） -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>

<!-- Apache PDFBox（用于解析 PDF 文件） -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>2.0.29</version>
</dependency>
```

**为什么需要这些依赖**：
- **Spring AI**：调用 LLM 进行简历解析
- **Redisson**：分布式锁实现
- **POI + PDFBox**：从 DOC/DOCX/PDF 中提取纯文本

#### 步骤 1.2：配置环境变量

在 `.env` 文件中添加：

```bash
# 智谱 AI 配置（推荐用于中文简历解析）
ZHIPU_API_KEY=xxxxxxxxxxxxx.xxxxxxxxxxxxx
ZHIPU_API_BASE=https://open.bigmodel.cn/api/paas/v4
ZHIPU_MODEL=glm-4-flash

# 模型说明：
# - glm-4-flash：速度快、价格低，适合开发测试（¥0.1/百万 tokens）
# - glm-4-air：性价比高，适合生产环境（¥0.5/百万 tokens）
# - glm-4：能力最强，适合复杂任务（¥1.0/百万 tokens）
```

**获取智谱 API Key**：
1. 访问 [智谱 AI 开放平台](https://open.bigmodel.cn/)
2. 注册并实名认证
3. 在「API Key」页面创建新密钥

**详细配置说明**：请参考 `docs/zhipu-ai-integration-guide.md`

#### 步骤 1.3：创建数据库表

执行 SQL 文件创建 candidates 表：

```bash
mysql -u smartats -p smartats < src/main/resources/db/candidates_table.sql
```

### 阶段二：候选人模块（2-3天）

#### 步骤 2.1：创建 Candidate 实体

**文件位置**：`src/main/java/com/smartats/module/candidate/entity/Candidate.java`

**关键点**：
- 使用 `@TableName` 指定表名
- JSON 字段使用 `@TableField(typeHandler = JsonTypeHandler.class)`
- 关联简历 ID 设置唯一索引

#### 步骤 2.2：创建 JSON 类型处理器

**为什么需要**：MyBatis-Plus 默认不支持 JSON 字段的自动序列化/反序列化

**文件位置**：`src/main/java/com/smartats/common/handler/JsonTypeHandler.java`

**功能**：
- 写入数据库：Java List/Map → JSON 字符串
- 读取数据库：JSON 字符串 → Java List/Map

#### 步骤 2.3：创建 CandidateMapper

**文件位置**：`src/main/java/com/smartats/module/candidate/mapper/CandidateMapper.java`

**关键方法**：
- `selectByResumeId(Long resumeId)`：根据简历 ID 查询候选人
- `selectByIdWithResume(Long id)`：关联查询简历信息

#### 步骤 2.4：创建 CandidateService

**文件位置**：`src/main/java/com/smartats/module/candidate/service/CandidateService.java`

**核心方法**：

```java
/**
 * 根据 AI 解析结果创建候选人记录
 */
@Transactional(rollbackFor = Exception.class)
public Candidate createCandidate(Long resumeId, CandidateInfo candidateInfo)

/**
 * 查询候选人详情（关联简历）
 */
public CandidateDetailResponse getCandidateDetail(Long candidateId)

/**
 * 更新候选人信息（手动修正）
 */
@Transactional(rollbackFor = Exception.class)
public void updateCandidate(Long candidateId, CandidateUpdateRequest request)
```

### 阶段三：AI 解析服务（2-3天）

#### 步骤 3.1：创建 Spring AI 配置

**文件位置**：`src/main/java/com/smartats/config/SpringAIConfig.java`

**配置内容**：
- OpenAI API 密钥和基础 URL
- 超时时间配置
- 重试策略配置
- 结构化输出配置

**为什么需要单独配置**：
- application.yml 中的配置可能不够灵活
- 可以根据不同环境切换不同的 AI 提供商

#### 步骤 3.2：创建文件内容提取服务

**文件位置**：`src/main/java/com/smartats/module/resume/service/ResumeContentExtractor.java`

**功能**：
- 从 MinIO 下载文件
- 根据文件类型提取文本：
  - PDF：使用 PDFBox
  - DOC/DOCX：使用 Apache POI
- 返回纯文本内容

**关键代码**：

```java
public String extractText(String fileUrl, String fileType) {
    // 1. 下载文件
    InputStream inputStream = downloadFromMinIO(fileUrl);

    // 2. 根据类型提取文本
    return switch (fileType) {
        case "application/pdf" -> extractFromPDF(inputStream);
        case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            -> extractFromDOCX(inputStream);
        case "application/msword" -> extractFromDOC(inputStream);
        default -> throw new BusinessException("不支持的文件类型");
    };
}
```

#### 步骤 3.3：创建 AI 解析服务

**文件位置**：`src/main/java/com/smartats/module/resume/service/ResumeParseService.java`

**核心方法**：

```java
/**
 * 使用 AI 解析简历内容
 */
public CandidateInfo parseResume(String resumeContent) {
    // 1. 构建 Prompt
    String prompt = buildParsePrompt(resumeContent);

    // 2. 调用 Spring AI
    ChatResponse response = chatModel.call(prompt);

    // 3. 解析结构化输出
    BeanOutputConverter<CandidateInfo> converter =
        new BeanOutputConverter<>(CandidateInfo.class);

    return converter.convert(response.getResult().getOutput().getContent());
}
```

**为什么使用 BeanOutputConverter**：
- 自动将 LLM 返回的 JSON 转换为 Java 对象
- 处理 JSON 解析异常
- 支持嵌套对象和集合

### 阶段四：集成到消费者（1-2天）

#### 步骤 4.1：修改 ResumeParseConsumer

**修改点**：
1. 引入 `ResumeParseService` 和 `CandidateService`
2. 使用 Redisson 替换 TODO 注释
3. 调用实际解析逻辑替换 `Thread.sleep(3000)`

**完整流程**：

```java
@RabbitListener(queues = RabbitMQConfig.RESUME_PARSE_QUEUE)
public void consumeResumeParse(ResumeParseMessage message, ...) {
    // 1. 幂等检查（已有）
    // 2. 获取分布式锁（使用 Redisson）
    RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + fileHash);

    try {
        boolean acquired = lock.tryLock(10, 300, TimeUnit.SECONDS);
        if (!acquired) {
            // 锁获取失败，跳过
            return;
        }

        // 3. 更新状态为 PROCESSING（已有）
        // 4. 查询简历（已有）

        // 5. 提取文件内容
        String content = contentExtractor.extractText(resume.getFileUrl(), resume.getFileType());
        updateTaskStatus(taskId, "PROCESSING", 30);

        // 6. AI 解析
        CandidateInfo candidateInfo = parseService.parseResume(content);
        updateTaskStatus(taskId, "PROCESSING", 70);

        // 7. 保存候选人信息
        Candidate candidate = candidateService.createCandidate(resume.getId(), candidateInfo);
        updateTaskStatus(taskId, "PROCESSING", 90);

        // 8. 更新简历状态（已有）
        // 9. 触发 Webhook（已有）
        // 10. 手动 ACK（已有）

    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

#### 步骤 4.2：添加 Redisson 配置

**文件位置**：`src/main/java/com/smartats/config/RedissonConfig.java`

**关键配置**：

```java
@Bean
public RedissonClient redissonClient() {
    Config config = new Config();

    // 单机模式
    SingleServerConfig serverConfig = config.useSingleServer();
    serverConfig.setAddress("redis://" + redisHost + ":" + redisPort);
    serverConfig.setPassword(redisPassword);

    // 看门狗配置
    serverConfig.setLockWatchdogTimeout(30000); // 30秒自动续期

    return Redisson.create(config);
}
```

### 阶段五：候选人管理接口（2-3天）

#### 步骤 5.1：创建查询接口

**文件位置**：`src/main/java/com/smartats/module/candidate/controller/CandidateController.java`

**接口列表**：

```java
/**
 * 查询候选人列表（分页）
 */
@GetMapping
public Result<PageResult<CandidateListResponse>> listCandidates(
    @RequestParam(required = false) String keyword,
    @RequestParam(required = false) String education,
    @RequestParam(required = false) Integer minWorkYears,
    @RequestParam(defaultValue = "1") Integer page,
    @RequestParam(defaultValue = "20") Integer size
)

/**
 * 查询候选人详情
 */
@GetMapping("/{id}")
public Result<CandidateDetailResponse> getCandidateDetail(@PathVariable Long id)

/**
 * 更新候选人信息（手动修正 AI 提取错误）
 */
@PutMapping("/{id}")
public Result<Void> updateCandidate(
    @PathVariable Long id,
    @RequestBody @Valid CandidateUpdateRequest request
)

/**
 * 删除候选人（级联删除简历）
 */
@DeleteMapping("/{id}")
public Result<Void> deleteCandidate(@PathVariable Long id)
```

#### 步骤 5.2：创建 DTO 类

**CandidateListResponse.java**：列表项（简略信息）
**CandidateDetailResponse.java**：详情（完整信息）
**CandidateUpdateRequest.java**：更新请求

---

## 完整代码示例

### 示例 1：Candidate 实体类

```java
package com.smartats.module.candidate.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.smartats.common.handler.JsonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 候选人实体
 */
@Data
@TableName("candidates")
public class Candidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 简历ID（1:1 关系）
     */
    private Long resumeId;

    // ========== 基本信息 ==========

    /**
     * 姓名
     */
    private String name;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 性别（男/女）
     */
    private String gender;

    /**
     * 年龄
     */
    private Integer age;

    // ========== 教育信息 ==========

    /**
     * 学历（本科/硕士/博士）
     */
    private String education;

    /**
     * 毕业院校
     */
    private String school;

    /**
     * 专业
     */
    private String major;

    /**
     * 毕业年份
     */
    private Integer graduationYear;

    // ========== 工作信息 ==========

    /**
     * 工作年限（年）
     */
    private Integer workYears;

    /**
     * 当前公司
     */
    private String currentCompany;

    /**
     * 当前职位
     */
    private String currentPosition;

    // ========== JSON 字段 ==========

    /**
     * 技能列表
     * 存储：["Java", "Spring", "MySQL", "Redis"]
     */
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<String> skills;

    /**
     * 工作经历
     * 存储：[{"company": "腾讯", "position": "后端工程师", "startDate": "2020-01", "endDate": "2023-01", "description": "..."}]
     */
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<Map<String, Object>> workExperience;

    /**
     * 项目经历
     */
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<Map<String, Object>> projectExperience;

    /**
     * 自我评价
     */
    private String selfEvaluation;

    // ========== AI 解析元数据 ==========

    /**
     * AI 解析的原始 JSON（用于调试和重新解析）
     */
    private String rawJson;

    /**
     * 置信度分数（0.00 - 1.00）
     * 用于判断解析质量，低于阈值需要人工审核
     */
    private Double confidenceScore;

    /**
     * 解析时间
     */
    private LocalDateTime parsedAt;

    // ========== 审计字段 ==========

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### 示例 2：JsonTypeHandler 类型处理器

```java
package com.smartats.common.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * JSON 类型处理器
 * <p>
 * 功能：
 * 1. 将 Java 对象（List/Map）序列化为 JSON 字符串存入数据库
 * 2. 将数据库的 JSON 字符串反序列化为 Java 对象
 * <p>
 * 使用场景：
 * - candidates.skills (List<String>)
 * - candidates.work_experience (List<Map<String, Object>>)
 */
@Slf4j
@MappedTypes({List.class, Map.class})
public class JsonTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        try {
            // Java 对象 → JSON 字符串
            ps.setString(i, objectMapper.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败: parameter={}", parameter, e);
            ps.setString(i, "[]");
        }
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }

    /**
     * 解析 JSON 字符串
     * <p>
     * 为什么需要判断类型：
     * - skills 是 List<String>
     * - work_experience 是 List<Map<String, Object>>
     * - 需要根据字段类型返回对应的 Java 类型
     */
    private Object parseJson(String json) {
        if (json == null || json.trim().isEmpty() || "null".equals(json)) {
            return null;
        }

        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败: json={}", json, e);
            return null;
        }
    }
}
```

**为什么需要 TypeHandler**：
- MyBatis 默认只支持基本类型（String, Integer, Date 等）
- JSON 字段需要自定义序列化/反序列化逻辑
- 继承 `BaseTypeHandler` 实现与 MyBatis 的无缝集成

### 示例 3：CandidateInfo DTO（AI 解析结果）

```java
package com.smartats.module.resume.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 解析的候选人信息
 * <p>
 * 设计说明：
 * 1. 与 LLM 返回的 JSON 结构完全对应
 * 2. 使用 Spring AI 的 BeanOutputConverter 自动转换
 * 3. 字段命名使用驼峰，符合 Java 规范
 */
@Data
public class CandidateInfo {

    /**
     * 姓名
     */
    private String name;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 性别（男/女）
     */
    private String gender;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 学历
     */
    private String education;

    /**
     * 毕业院校
     */
    private String school;

    /**
     * 专业
     */
    private String major;

    /**
     * 毕业年份
     */
    private Integer graduationYear;

    /**
     * 工作年限
     */
    private Integer workYears;

    /**
     * 当前公司
     */
    private String currentCompany;

    /**
     * 当前职位
     */
    private String currentPosition;

    /**
     * 技能列表
     * 示例：["Java", "Spring Boot", "MySQL", "Redis", "RabbitMQ"]
     */
    private List<String> skills;

    /**
     * 工作经历
     * 示例：
     * [
     *   {
     *     "company": "腾讯",
     *     "position": "后端工程师",
     *     "startDate": "2020-01",
     *     "endDate": "2023-01",
     *     "description": "负责..."
     *   }
     * ]
     */
    private List<WorkExperience> workExperience;

    /**
     * 项目经历
     */
    private List<ProjectExperience> projectExperience;

    /**
     * 自我评价
     */
    private String selfEvaluation;

    /**
     * 工作经历内部类
     */
    @Data
    public static class WorkExperience {
        private String company;
        private String position;
        private String startDate;
        private String endDate;
        private String description;
    }

    /**
     * 项目经历内部类
     */
    @Data
    public static class ProjectExperience {
        private String name;
        private String role;
        private String startDate;
        private String endDate;
        private String description;
        private List<String> technologies;
    }
}
```

### 示例 4：Spring AI 配置（智谱 AI）

```java
package com.smartats.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置（智谱 AI）
 * <p>
 * 说明：智谱 AI 兼容 OpenAI API 格式
 * 功能：
 * 1. 配置智谱 API 密钥和基础 URL
 * 2. 设置超时时间和温度参数
 * 3. 支持不同模型切换（GLM-4-Flash/Air/Plus）
 */
@Configuration
public class SpringAIConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:glm-4-flash}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private Double temperature;

    /**
     * 创建智谱 AI Chat Model
     * <p>
     * 为什么使用 OpenAiChatModel：
     * - 智谱 AI 兼容 OpenAI API 格式
     * - 更细粒度的控制
     * - 支持结构化输出（BeanOutputConverter）
     */
    @Bean
    public OpenAiChatModel openAiChatModel() {
        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)           // glm-4-flash / glm-4-air / glm-4
                .withTemperature(temperature)  // 0.3（较低温度，更确定的输出）
                .withMaxTokens(4000)        // 最大输出长度
                .build();

        return new OpenAiChatModel(openAiApi, options);
    }
}
```

**application.yml 配置**：

```yaml
spring:
  ai:
    openai:
      # 智谱 API Key（从环境变量读取）
      api-key: ${ZHIPU_API_KEY}
      # 智谱 API 地址
      base-url: ${ZHIPU_API_BASE:https://open.bigmodel.cn/api/paas/v4}
      chat:
        options:
          # 模型选择
          model: ${ZHIPU_MODEL:glm-4-flash}
          # 温度（0-1，简历解析建议使用 0.3 获得更确定的输出）
          temperature: 0.3
          # 最大 token 数
          max-tokens: 4000
```

**为什么使用智谱 AI**：
- ✅ 国内访问稳定，无需代理
- ✅ 中文理解能力强，专门针对中文优化
- ✅ 价格更便宜（GLM-4-Flash 仅 ¥0.1/百万 tokens）
- ✅ 兼容 OpenAI API 格式，代码无需大改

**详细配置说明**：请参考 `docs/zhipu-ai-integration-guide.md`

### 示例 5：文件内容提取服务

```java
package com.smartats.module.resume.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

/**
 * 简历内容提取服务
 * <p>
 * 功能：
 * 1. 从 MinIO 下载文件
 * 2. 根据文件类型提取文本：
 *    - PDF：使用 Apache PDFBox
 *    - DOCX：使用 Apache POI (XWPF)
 *    - DOC：使用 Apache POI (HWPF)
 * 3. 返回纯文本内容供 AI 解析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeContentExtractor {

    private final FileStorageService fileStorageService;

    /**
     * 从文件 URL 提取文本内容
     *
     * @param fileUrl  MinIO 文件 URL
     * @param fileType 文件类型（Content-Type）
     * @return 纯文本内容
     */
    public String extractText(String fileUrl, String fileType) {
        log.info("开始提取文件内容: fileUrl={}, fileType={}", fileUrl, fileType);

        try {
            // 1. 从 URL 下载文件流
            URL url = new URL(fileUrl);
            InputStream inputStream = url.openStream();

            // 2. 根据文件类型提取文本
            String text = switch (fileType) {
                case "application/pdf" -> extractFromPDF(inputStream);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        -> extractFromDOCX(inputStream);
                case "application/msword" -> extractFromDOC(inputStream);
                default -> throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的文件类型: " + fileType);
            };

            inputStream.close();

            log.info("文件内容提取成功: textLength={}", text.length());
            return text;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件内容提取失败: fileUrl={}", fileUrl, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件解析失败");
        }
    }

    /**
     * 从 PDF 提取文本
     * <p>
     * 使用 PDFBox：
     * - 成熟稳定的 PDF 处理库
     * - 支持中文（需要额外配置字体）
     * - 处理复杂布局可能有误差
     */
    private String extractFromPDF(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // 设置排序，保持文本顺序
            stripper.setSortByPosition(true);

            return stripper.getText(document);
        }
    }

    /**
     * 从 DOCX 提取文本
     * <p>
     * 使用 Apache POI (XWPF)：
     * - OOXML 格式（Office 2007+）
     * - 提取所有段落文本
     */
    private String extractFromDOCX(InputStream inputStream) throws Exception {
        StringBuilder text = new StringBuilder();

        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            for (XWPFParagraph paragraph : paragraphs) {
                text.append(paragraph.getText()).append("\n");
            }
        }

        return text.toString();
    }

    /**
     * 从 DOC 提取文本
     * <p>
     * 使用 Apache POI (HWPF)：
     * - 旧版 Word 格式（Office 2003-）
     * - HWPF = Horrible Word Processor Format
     */
    private String extractFromDOC(InputStream inputStream) throws Exception {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {

            return extractor.getText();
        }
    }
}
```

### 示例 6：AI 解析服务（智谱 AI + 中文简历优化）

```java
package com.smartats.module.resume.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.resume.dto.CandidateInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI 简历解析服务（智谱 AI）
 * <p>
 * 功能：
 * 1. 接收纯文本简历内容
 * 2. 构建 Prompt（针对中文简历优化）让 LLM 提取结构化信息
 * 3. 使用 BeanOutputConverter 确保返回符合格式的 JSON
 * 4. 返回 CandidateInfo 对象
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParseService {

    private final ChatClient chatClient;

    @Value("${spring.ai.openai.chat.options.model:glm-4-flash}")
    private String model;

    /**
     * 解析简历内容
     *
     * @param resumeContent 简历纯文本内容
     * @return 结构化的候选人信息
     */
    public CandidateInfo parseResume(String resumeContent) {
        log.info("开始使用智谱 AI 解析简历: model={}, contentLength={}", model, resumeContent.length());

        try {
            // 1. 创建结构化输出转换器
            BeanOutputConverter<CandidateInfo> converter =
                    new BeanOutputConverter<>(CandidateInfo.class);

            // 2. 获取 JSON 格式说明
            String formatInstructions = converter.getFormat();

            // 3. 构建 Prompt（针对中文简历优化）
            String prompt = buildPromptForChineseResume(resumeContent, formatInstructions);

            // 4. 调用智谱 AI
            log.debug("发送请求到智谱 AI: model={}", model);

            Prompt aiPrompt = new Prompt(new UserMessage(prompt));
            ChatResponse response = chatClient.call(aiPrompt);

            String responseContent = response.getResult().getOutput().getContent();
            log.debug("智谱 AI 响应: responseLength={}", responseContent.length());

            // 5. 解析响应
            CandidateInfo candidateInfo = converter.convert(responseContent);

            log.info("智谱 AI 解析成功: name={}, phone={}, email={}",
                    candidateInfo.getName(), candidateInfo.getPhone(), candidateInfo.getEmail());

            return candidateInfo;

        } catch (Exception e) {
            log.error("智谱 AI 解析失败", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "简历解析失败: " + e.getMessage());
        }
    }

    /**
     * 构建针对中文简历优化的 Prompt
     * <p>
     * 优化点：
     * 1. 使用中文描述，智谱 AI 对中文理解更好
     * 2. 针对中文简历格式调整字段说明
     * 3. 添加中文简历常见格式转换规则
     * 4. 提供更详细的提取示例
     */
    private String buildPromptForChineseResume(String resumeContent, String formatInstructions) {
        return String.format("""
                你是一个专业的简历信息提取助手。请从以下中文简历内容中提取结构化信息，并以 JSON 格式返回。

                ## 提取字段说明

                ### 基本信息
                - name: 姓名
                - phone: 手机号（11位数字，如：13800138000）
                - email: 邮箱地址（如：zhangsan@example.com）
                - gender: 性别（男/女，如果无法判断返回 null）
                - age: 年龄（整数）

                ### 教育信息
                - education: 学历（高中/专科/本科/硕士研究生/博士研究生/MBA）
                - school: 毕业院校全称
                - major: 专业名称
                - graduationYear: 毕业年份（4位整数，如：2020）

                ### 工作信息
                - workYears: 工作年限（整数年，如：3）
                - currentCompany: 当前或最近一家公司名称
                - currentPosition: 当前或最近职位名称

                ### 技能与经历
                - skills: 技能列表（字符串数组，提取核心技能，如：["Java", "Spring Boot", "MySQL", "Redis"]）
                - workExperience: 工作经历数组，每项包含：
                  * company: 公司名称
                  * position: 职位名称
                  * startDate: 开始时间（格式：yyyy-MM 或 yyyy年MM月）
                  * endDate: 结束时间（格式：yyyy-MM 或 "至今"）
                  * description: 工作职责和成就描述

                - projectExperience: 项目经历数组，每项包含：
                  * name: 项目名称
                  * role: 担任角色
                  * startDate: 开始时间（yyyy-MM）
                  * endDate: 结束时间（yyyy-MM）
                  * description: 项目描述和职责
                  * technologies: 使用的技术栈（字符串数组）

                - selfEvaluation: 自我评价（原文提取）

                ## 注意事项

                1. 如果某个字段无法从简历中提取，使用 null 而不是猜测
                2. 日期格式统一为 yyyy-MM，如果写"2020年1月"，转换为"2020-01"
                3. 技能列表只保留核心技术技能，不要包含"办公软件"、"英语"等通用技能
                4. 工作经历和项目经历按时间倒序排列（最新的在前）
                5. 公司名称和项目名称保留完整，不要缩写
                6. 职位名称使用标准称呼，如"后端开发工程师"而不是"后端"
                7. 只返回 JSON 数据，不要包含任何其他文字说明、markdown 代码块标记

                ## 日期格式转换规则

                - "2020年1月" → "2020-01"
                - "2020.01" → "2020-01"
                - "2020/01" → "2020-01"
                - "2020年01月至今" → endDate 为 "至今"
                - "2020年至今" → "2020-01"，endDate 为 "至今"

                ## 学历标准化

                - "大学本科"、"本科"、"学士" → "本科"
                - "硕士研究生"、"硕士"、"研究生硕士" → "硕士研究生"
                - "博士研究生"、"博士" → "博士研究生"

                ## 职位标准化

                - "Java开发"、"Java工程师" → "Java开发工程师"
                - "后端"、"后端开发" → "后端开发工程师"

                ## 输出格式要求

                %s

                ## 简历内容

                %s

                请严格按照上述格式提取并返回 JSON：
                """,
                formatInstructions,
                resumeContent
        );
    }
}
```

**关键优化点**：

1. **中文 Prompt**：智谱 AI 对中文理解更好，使用中文 Prompt 提高准确率
2. **日期格式转换**：针对中文简历常见的 `2020年1月` 格式添加转换规则
3. **学历标准化**：统一各种学历表达方式
4. **职位标准化**：将简写转换为标准称呼
5. **格式约束**：明确要求不包含 markdown 代码块标记

**为什么这样优化**：
- 中文简历格式多样，需要明确转换规则
- 智谱 GLM 模型针对中文优化，中文 Prompt 效果更好
- 减少后处理工作，让 AI 直接返回标准格式
                %s

                请提取并返回 JSON：
                """,
                formatInstructions,
                resumeContent
        );
    }
}
```

### 示例 7：CandidateService

```java
package com.smartats.module.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.candidate.dto.CandidateDetailResponse;
import com.smartats.module.candidate.dto.CandidateListResponse;
import com.smartats.module.candidate.dto.CandidateUpdateRequest;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.resume.dto.CandidateInfo;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 候选人服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateMapper candidateMapper;
    private final ResumeMapper resumeMapper;
    private final ObjectMapper objectMapper;

    /**
     * 根据 AI 解析结果创建候选人记录
     * <p>
     * 为什么需要事务：
     * 1. 插入 candidates 表
     * 2. 更新 resumes 表状态
     * 两个操作必须同时成功或同时失败
     */
    @Transactional(rollbackFor = Exception.class)
    public Candidate createCandidate(Long resumeId, CandidateInfo candidateInfo) {
        log.info("创建候选人记录: resumeId={}", resumeId);

        // 1. 检查是否已存在
        LambdaQueryWrapper<Candidate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Candidate::getResumeId, resumeId);
        Candidate existing = candidateMapper.selectOne(wrapper);

        if (existing != null) {
            log.warn("候选人已存在: candidateId={}, resumeId={}", existing.getId(), resumeId);
            return existing;
        }

        // 2. 创建 Candidate 实体
        Candidate candidate = new Candidate();
        candidate.setResumeId(resumeId);

        // 基本信息
        candidate.setName(candidateInfo.getName());
        candidate.setPhone(candidateInfo.getPhone());
        candidate.setEmail(candidateInfo.getEmail());
        candidate.setGender(candidateInfo.getGender());
        candidate.setAge(candidateInfo.getAge());

        // 教育信息
        candidate.setEducation(candidateInfo.getEducation());
        candidate.setSchool(candidateInfo.getSchool());
        candidate.setMajor(candidateInfo.getMajor());
        candidate.setGraduationYear(candidateInfo.getGraduationYear());

        // 工作信息
        candidate.setWorkYears(candidateInfo.getWorkYears());
        candidate.setCurrentCompany(candidateInfo.getCurrentCompany());
        candidate.setCurrentPosition(candidateInfo.getCurrentPosition());

        // JSON 字段
        candidate.setSkills(candidateInfo.getSkills());
        candidate.setWorkExperience(candidateInfo.getWorkExperience());
        candidate.setProjectExperience(candidateInfo.getProjectExperience());
        candidate.setSelfEvaluation(candidateInfo.getSelfEvaluation());

        // 元数据
        candidate.setParsedAt(LocalDateTime.now());
        candidate.setConfidenceScore(0.85); // TODO: 根据解析质量动态计算

        try {
            // 保存原始 JSON（用于调试）
            candidate.setRawJson(objectMapper.writeValueAsString(candidateInfo));
        } catch (JsonProcessingException e) {
            log.warn("序列化原始 JSON 失败", e);
        }

        // 3. 保存到数据库
        candidateMapper.insert(candidate);

        // 4. 更新简历状态
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume != null) {
            resume.setStatus("COMPLETED");
            resumeMapper.updateById(resume);
        }

        log.info("候选人创建成功: candidateId={}, resumeId={}, name={}",
                candidate.getId(), resumeId, candidate.getName());

        return candidate;
    }

    /**
     * 查询候选人列表（分页）
     */
    public Page<CandidateListResponse> listCandidates(
            String keyword,
            String education,
            Integer minWorkYears,
            Integer page,
            Integer size
    ) {
        Page<Candidate> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Candidate> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索（姓名、手机、邮箱）
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Candidate::getName, keyword)
                    .or().like(Candidate::getPhone, keyword)
                    .or().like(Candidate::getEmail, keyword)
            );
        }

        // 学历筛选
        if (education != null && !education.isBlank()) {
            wrapper.eq(Candidate::getEducation, education);
        }

        // 工作年限筛选
        if (minWorkYears != null && minWorkYears > 0) {
            wrapper.ge(Candidate::getWorkYears, minWorkYears);
        }

        // 按创建时间倒序
        wrapper.orderByDesc(Candidate::getCreatedAt);

        Page<Candidate> resultPage = candidateMapper.selectPage(pageParam, wrapper);

        // 转换为 DTO
        return resultPage.convert(candidate -> {
            CandidateListResponse response = new CandidateListResponse();
            response.setId(candidate.getId());
            response.setResumeId(candidate.getResumeId());
            response.setName(candidate.getName());
            response.setPhone(candidate.getPhone());
            response.setEmail(candidate.getEmail());
            response.setEducation(candidate.getEducation());
            response.setWorkYears(candidate.getWorkYears());
            response.setCurrentCompany(candidate.getCurrentCompany());
            response.setCurrentPosition(candidate.getCurrentPosition());
            response.setCreatedAt(candidate.getCreatedAt());
            return response;
        });
    }

    /**
     * 查询候选人详情
     */
    public CandidateDetailResponse getCandidateDetail(Long candidateId) {
        Candidate candidate = candidateMapper.selectById(candidateId);

        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "候选人不存在");
        }

        // 查询关联的简历
        Resume resume = resumeMapper.selectById(candidate.getResumeId());

        // 组装返回结果
        CandidateDetailResponse response = new CandidateDetailResponse();
        response.setId(candidate.getId());
        response.setResumeId(candidate.getResumeId());
        response.setResume(resume);
        // ... 其他字段

        return response;
    }

    /**
     * 更新候选人信息（手动修正 AI 提取错误）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCandidate(Long candidateId, CandidateUpdateRequest request) {
        Candidate candidate = candidateMapper.selectById(candidateId);

        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "候选人不存在");
        }

        // 更新字段
        if (request.getName() != null) {
            candidate.setName(request.getName());
        }
        if (request.getPhone() != null) {
            candidate.setPhone(request.getPhone());
        }
        // ... 其他字段

        candidateMapper.updateById(candidate);

        log.info("候选人信息已更新: candidateId={}", candidateId);
    }
}
```

### 示例 8：RedissonConfig 配置

```java
package com.smartats.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置
 * <p>
 * 功能：
 * 1. 配置 Redis 单机模式连接
 * 2. 设置看门狗超时时间（自动续期）
 * 3. 提供分布式锁实例
 * <p>
 * 看门狗机制：
 * - 默认锁过期时间 30 秒
 * - 如果业务未执行完，看门狗每 10 秒自动续期
 * - 业务执行完成后释放锁
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * 创建 RedissonClient
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 单机模式配置
        String address = "redis://" + redisHost + ":" + redisPort;

        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setDatabase(0)
                // 连接池配置
                .setConnectionPoolSize(20)
                .setConnectionMinimumIdleSize(5)
                // 看门狗配置（30秒超时，每10秒续期）
                .setLockWatchdogTimeout(30000)
                // 重试配置
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }
}
```

### 示例 9：修改后的 ResumeParseConsumer

```java
package com.smartats.module.resume.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.smartats.config.RabbitMQConfig;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.service.CandidateService;
import com.smartats.module.resume.dto.CandidateInfo;
import com.smartats.module.resume.dto.ResumeParseMessage;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.service.ResumeContentExtractor;
import com.smartats.module.resume.service.ResumeParseService;
import com.smartats.module.resume.mapper.ResumeMapper;
import com.smartats.module.webhook.enums.WebhookEventType;
import com.smartats.module.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 简历解析消费者
 * <p>
 * 功能：
 * 1. 监听 RabbitMQ 队列
 * 2. 幂等性检查
 * 3. 分布式锁（防止重复解析）
 * 4. AI 解析简历
 * 5. 保存候选人信息
 * 6. 触发 Webhook 事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParseConsumer {

    private final ResumeMapper resumeMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;

    // 新增依赖
    private final ResumeContentExtractor contentExtractor;
    private final ResumeParseService parseService;
    private final CandidateService candidateService;
    private final RedissonClient redissonClient;

    private static final String TASK_STATUS_KEY_PREFIX = "task:resume:";
    private static final String LOCK_KEY_PREFIX = "lock:resume:";

    /**
     * 消费简历解析消息
     */
    @RabbitListener(queues = RabbitMQConfig.RESUME_PARSE_QUEUE)
    public void consumeResumeParse(
            ResumeParseMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {
        String taskId = message.getTaskId();
        Long resumeId = message.getResumeId();
        String fileHash = message.getFileHash();

        log.info("收到简历解析消息: taskId={}, resumeId={}", taskId, resumeId);

        // 1. 幂等检查（Redis 标记）
        String idempotentKey = "idempotent:resume:" + resumeId;
        Boolean alreadyProcessed = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", 1, java.util.concurrent.TimeUnit.HOURS);

        if (Boolean.FALSE.equals(alreadyProcessed)) {
            log.warn("简历已处理过，跳过: resumeId={}", resumeId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        // 2. 获取分布式锁（防止重复解析）
        String lockKey = LOCK_KEY_PREFIX + fileHash;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，最多等待 10 秒，锁自动释放时间 300 秒（5分钟）
            boolean acquired = lock.tryLock(10, 300, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("获取锁失败，文件正在被其他实例处理: fileHash={}", fileHash);
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("获取锁成功: lockKey={}", lockKey);

            // 3. 更新任务状态为 PROCESSING
            updateTaskStatus(taskId, "PROCESSING", 10);

            // 4. 查询简历信息
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume == null) {
                log.error("简历不存在: resumeId={}", resumeId);
                updateTaskStatus(taskId, "FAILED", 0, "简历不存在");
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 5. 提取文件内容
            log.info("开始提取文件内容: resumeId={}, fileName={}", resumeId, resume.getFileName());
            String content = contentExtractor.extractText(resume.getFileUrl(), resume.getFileType());
            log.info("文件内容提取成功: contentLength={}", content.length());

            updateTaskStatus(taskId, "PROCESSING", 30);

            // 6. AI 解析
            log.info("开始 AI 解析: resumeId={}", resumeId);
            CandidateInfo candidateInfo = parseService.parseResume(content);
            log.info("AI 解析成功: name={}, phone={}, email={}",
                    candidateInfo.getName(), candidateInfo.getPhone(), candidateInfo.getEmail());

            updateTaskStatus(taskId, "PROCESSING", 70);

            // 7. 保存候选人信息
            log.info("开始保存候选人信息: resumeId={}", resumeId);
            Candidate candidate = candidateService.createCandidate(resumeId, candidateInfo);
            log.info("候选人信息保存成功: candidateId={}, name={}", candidate.getId(), candidate.getName());

            updateTaskStatus(taskId, "PROCESSING", 90);

            // 8. 更新任务状态为 COMPLETED
            updateTaskStatus(taskId, "COMPLETED", 100);

            log.info("简历解析完成: taskId={}, resumeId={}", taskId, resumeId);

            // 9. 触发 Webhook 事件
            triggerWebhookEvent(WebhookEventType.RESUME_PARSE_COMPLETED, resume, taskId, null, candidate);

            // 10. 手动确认消息
            channel.basicAck(deliveryTag, false);

        } catch (InterruptedException e) {
            log.error("简历解析被中断: taskId={}", taskId, e);
            handleFailedTask(taskId, "解析被中断");
            retryOrReject(channel, deliveryTag, message);

        } catch (Exception e) {
            log.error("简历解析失败: taskId={}", taskId, e);
            handleFailedTask(taskId, "解析失败: " + e.getMessage());

            // 获取简历信息用于 Webhook
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume != null) {
                triggerWebhookEvent(WebhookEventType.RESUME_PARSE_FAILED, resume, taskId, e.getMessage(), null);
            }

            retryOrReject(channel, deliveryTag, message);

        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("释放锁成功: lockKey={}", lockKey);
            }
        }
    }

    /**
     * 触发 Webhook 事件
     */
    private void triggerWebhookEvent(
            WebhookEventType eventType,
            Resume resume,
            String taskId,
            String errorMessage,
            Candidate candidate
    ) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("resumeId", resume.getId());
            data.put("fileName", resume.getFileName());
            data.put("userId", resume.getUserId());
            data.put("status", resume.getStatus());

            if (errorMessage != null) {
                data.put("errorMessage", errorMessage);
            }

            if (candidate != null) {
                data.put("candidateId", candidate.getId());
                data.put("candidateName", candidate.getName());
            }

            webhookService.sendEvent(eventType, data);
        } catch (Exception e) {
            log.error("触发 Webhook 事件失败: event={}, resumeId={}", eventType.getCode(), resume.getId(), e);
        }
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatus(String taskId, String status, int progress) throws Exception {
        updateTaskStatus(taskId, status, progress, null);
    }

    private void updateTaskStatus(String taskId, String status, int progress, String errorMessage) throws Exception {
        String taskKey = TASK_STATUS_KEY_PREFIX + taskId;

        TaskStatusResponse taskStatus = new TaskStatusResponse();
        taskStatus.setStatus(status);
        taskStatus.setProgress(progress);
        taskStatus.setErrorMessage(errorMessage);

        String json = objectMapper.writeValueAsString(taskStatus);
        redisTemplate.opsForValue().set(taskKey, json, 24, java.util.concurrent.TimeUnit.HOURS);

        log.info("更新任务状态: taskId={}, status={}, progress={}", taskId, status, progress);
    }

    /**
     * 处理失败任务
     */
    private void handleFailedTask(String taskId, String errorMessage) {
        try {
            updateTaskStatus(taskId, "FAILED", 0, errorMessage);
        } catch (Exception e) {
            log.error("更新失败任务状态异常: taskId={}", taskId, e);
        }
    }

    /**
     * 重试或拒绝消息
     */
    private void retryOrReject(Channel channel, long deliveryTag, ResumeParseMessage message) throws IOException {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();

        if (retryCount < 3) {
            log.info("消息重试: retryCount={}", retryCount);
            // TODO: 重新发送到队列，增加重试次数
            channel.basicNack(deliveryTag, false, true);
        } else {
            log.error("消息重试次数超限，进入死信队列: retryCount={}", retryCount);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
```

### 示例 10：CandidateController

```java
package com.smartats.module.candidate.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.result.PageResult;
import com.smartats.common.result.Result;
import com.smartats.module.candidate.dto.CandidateDetailResponse;
import com.smartats.module.candidate.dto.CandidateListResponse;
import com.smartats.module.candidate.dto.CandidateUpdateRequest;
import com.smartats.module.candidate.service.CandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 候选人管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    /**
     * 查询候选人列表（分页）
     * <p>
     * 权限：需要登录
     */
    @GetMapping
    public Result<PageResult<CandidateListResponse>> listCandidates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) Integer minWorkYears,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        log.info("查询候选人列表: keyword={}, education={}, minWorkYears={}", keyword, education, minWorkYears);

        Page<CandidateListResponse> resultPage = candidateService.listCandidates(
                keyword, education, minWorkYears, page, size
        );

        PageResult<CandidateListResponse> pageResult = PageResult.of(resultPage);
        return Result.success(pageResult);
    }

    /**
     * 查询候选人详情
     */
    @GetMapping("/{id}")
    public Result<CandidateDetailResponse> getCandidateDetail(@PathVariable Long id) {
        log.info("查询候选人详情: candidateId={}", id);

        CandidateDetailResponse response = candidateService.getCandidateDetail(id);

        return Result.success(response);
    }

    /**
     * 更新候选人信息（手动修正 AI 提取错误）
     * <p>
     * 权限：需要 HR 或 ADMIN 角色
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public Result<Void> updateCandidate(
            @PathVariable Long id,
            @RequestBody @Validated CandidateUpdateRequest request
    ) {
        log.info("更新候选人信息: candidateId={}, request={}", id, request);

        candidateService.updateCandidate(id, request);

        return Result.success();
    }

    /**
     * 删除候选人（级联删除简历）
     * <p>
     * 权限：需要 ADMIN 角色
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteCandidate(@PathVariable Long id) {
        log.info("删除候选人: candidateId={}", id);

        candidateService.deleteCandidate(id);

        return Result.success();
    }
}
```

---

## 为什么这样设计

### 1. 为什么使用异步架构

**问题**：简历 AI 解析需要 5-10 秒，如果同步处理用户会等待很久

**解决方案**：
- 用户上传 → 立即返回 taskId
- 后台异步解析 → 更新 Redis 状态
- 前端轮询查询 → 获取解析结果

**好处**：
- 用户体验好（不用等待）
- 系统吞吐量高（可以并发处理多个任务）
- 削峰填谷（RabbitMQ 缓冲请求）

### 2. 为什么使用分布式锁

**问题**：
- 同一个文件可能被多次上传（网络重试、用户重复点击）
- 如果多个消费者同时处理，会重复调用 AI 浪费钱

**解决方案**：使用 Redisson 分布式锁

```java
RLock lock = redissonClient.getLock("lock:resume:" + fileHash);
lock.tryLock(10, 300, TimeUnit.SECONDS);
```

**为什么用 Redisson 而不是简单的 Redis SETNX**：
- **看门狗机制**：自动续期，防止业务没执行完锁就释放
- **可重入**：同一线程可以多次获取锁
- **公平锁**：支持先来先得（可选）

### 3. 为什么 candidates 和 resumes 分表

**问题**：为什么不分两个表，直接把解析结果存在 resumes 表？

**设计方案**：
- `resumes` 表：文件元数据（文件名、大小、路径、状态）
- `candidates` 表：结构化候选人信息（姓名、电话、工作经历）

**好处**：
- **职责分离**：文件管理 vs 数据管理
- **扩展性强**：一个简历可能有多个版本解析结果
- **查询性能**：列表查询只查 resumes，详情查询再 JOIN candidates
- **数据完整性**：AI 解析失败不影响简历记录

### 4. 为什么使用 JSON 字段

**问题**：工作经历、项目经历结构复杂，字段不固定

**传统方案**：创建工作经历表、项目经历表
```sql
-- 需要 3 张表
candidates
work_experiences (candidate_id, company, position, ...)
project_experiences (candidate_id, name, role, ...)
```

**JSON 方案**：直接存储在 candidates 表
```sql
-- 只需要 1 张表
candidates (
    ...
    work_experience JSON,
    project_experience JSON
)
```

**为什么选择 JSON**：
- **简单**：不需要额外表和关联查询
- **灵活**：AI 提取的字段可能变化
- **性能**：MySQL 5.7+ 支持JSON 索引和查询
- **够用**：工作经历一般不需要复杂查询

### 5. 为什么需要幂等性检查

**问题**：
- RabbitMQ 可能重复投递消息（网络抖动、消费者重启）
- 没有幂等性会导致同一个简历被解析多次

**解决方案**：
```java
String idempotentKey = "idempotent:resume:" + resumeId;
Boolean alreadyProcessed = redisTemplate.opsForValue()
        .setIfAbsent(idempotentKey, "1", 1, HOURS);
```

**为什么放在消费者而不是生产者**：
- 消费者才是真正处理业务的地方
- 即使生产者去重，RabbitMQ 层面仍可能重复

### 6. 为什么使用 Spring AI

**问题**：为什么不直接调用 OpenAI API？

**直接调用的问题**：
```java
// 需要手动处理 HTTP 请求、重试、错误
RestTemplate restTemplate = new RestTemplate();
String response = restTemplate.postForObject(apiUrl, request, String.class);
```

**Spring AI 的好处**：
- **统一抽象**：切换 AI 提供商只需要改配置
- **结构化输出**：`BeanOutputConverter` 自动转换 JSON → Java
- **自动重试**：网络错误自动重试
- **流式输出**：支持 SSE（可选）

### 7. 为什么需要 FileContentExtractor

**问题**：AI 不能直接处理 PDF/Word 文件，需要纯文本

**设计方案**：
- PDF：使用 Apache PDFBox
- DOCX：使用 Apache POI (XWPF)
- DOC：使用 Apache POI (HWPF)

**为什么单独一个服务**：
- **职责单一**：只负责文件 → 文本
- **易于测试**：可以单独测试各种文件格式
- **可扩展**：未来支持更多格式（TXT、Markdown）

---

## 测试验证

### 1. 单元测试

**测试 CandidateService**：
```java
@SpringBootTest
class CandidateServiceTest {

    @Autowired
    private CandidateService candidateService;

    @Test
    void testCreateCandidate() {
        // 构造测试数据
        CandidateInfo info = new CandidateInfo();
        info.setName("张三");
        info.setPhone("13800138000");
        info.setEmail("zhangsan@example.com");

        // 调用服务
        Candidate candidate = candidateService.createCandidate(1L, info);

        // 验证结果
        assertNotNull(candidate);
        assertEquals("张三", candidate.getName());
    }
}
```

### 2. 集成测试

**测试完整流程**：
```java
@SpringBootTest
@AutoConfigureMockMvc
class ResumeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testUploadAndParse() throws Exception {
        // 1. 上传简历
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                Files.readAllBytes(Path.of("test-resume.pdf"))
        );

        String response = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 2. 解析 taskId
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);
        String taskId = root.path("data").path("taskId").asText();

        // 3. 轮询查询状态
        await().atMost(30, SECONDS).until(() -> {
            String statusResponse = mockMvc.perform(get("/api/v1/resumes/tasks/" + taskId))
                    .andReturn().getResponse().getContentAsString();

            JsonNode statusRoot = mapper.readTree(statusResponse);
            String status = statusRoot.path("data").path("status").asText();

            return "COMPLETED".equals(status) || "FAILED".equals(status);
        });
    }
}
```

### 3. 手动测试

**使用 Postman 测试**：

1. **上传简历**
```http
POST http://localhost:8080/api/v1/resumes/upload
Content-Type: multipart/form-data
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

------WebKitFormBoundary
Content-Disposition: form-data; name="file"; filename="resume.pdf"
Content-Type: application/pdf

<binary file data>
------WebKitFormBoundary--
```

2. **查询任务状态**
```http
GET http://localhost:8080/api/v1/resumes/tasks/{taskId}
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

3. **查询候选人列表**
```http
GET http://localhost:8080/api/v1/candidates?page=1&size=20
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## 常见问题

### Q1: AI 解析失败怎么办？

**可能原因**：
- 文件格式损坏
- AI API 调用失败
- 返回的 JSON 格式错误

**解决方案**：
1. 状态更新为 FAILED，记录错误信息
2. 消息进入死信队列
3. 提供手动重试接口
4. Webhook 通知失败事件

### Q2: 如何提高 AI 解析准确率？

**优化方向**：
1. **Prompt 优化**：更详细的字段说明和示例
2. **模型选择**：使用更强的模型（GPT-4 vs GPT-3.5）
3. **后处理**：正则表达式验证手机号、邮箱格式
4. **人工审核**：置信度低于阈值的标记需要审核
5. **用户反馈**：提供"修正"功能，收集错误数据微调

### Q3: 如何处理并发上传同一个文件？

**三层防护**：
1. **Redis 去重**：上传时检查 `dedup:resume:{md5}`
2. **数据库唯一索引**：`file_hash` 字段 UNIQUE 约束
3. **分布式锁**：消费时加锁 `lock:resume:{md5}`

### Q4: 如何监控 AI 解析性能？

**监控指标**：
- 解析成功率（COMPLETED / 总数）
- 平均解析时长
- AI API 调用次数和费用
- 错误类型分布

**实现方式**：
- 使用 Micrometer + Prometheus
- 在关键节点记录 metrics
- Grafana 可视化展示

---

## 下一步计划

完成上述功能后，可以继续开发：

### 1. 向量搜索（RAG）
- 将候选人信息向量化
- 语义搜索（找"Java 后端，3年经验，有电商项目"的候选人）
- 使用 Milvus 或 PgVector

### 2. 智能推荐
- 根据职位要求推荐候选人
- 计算匹配度分数

### 3. 简历比对
- 对比两个简历的相似度
- 发现简历抄袭

### 4. 批量操作
- 批量上传、批量删除
- 批量导出候选人信息

---

**文档结束**

建议按照以下顺序实现：
1. ✅ 候选人模块（Candidate 实体、Mapper、Service）
2. ✅ JSON 类型处理器
3. ✅ Spring AI 配置
4. ✅ 文件内容提取服务
5. ✅ AI 解析服务
6. ✅ Redisson 配置
7. ✅ 修改消费者集成所有服务
8. ✅ 候选人管理接口
9. ✅ 测试验证
