# SmartATS 简历模块完善开发手册

**文档版本**: v2.0
**创建日期**: 2026-02-20
**最后更新**: 2026-02-20
**技术选型**: Spring AI 官方 + 智谱 AI GLM
**目标**: 完善简历模块的 AI 解析和候选人管理功能

---

## 📋 目录

1. [技术选型决策](#技术选型决策)
2. [当前状态分析](#当前状态分析)
3. [待完善功能清单](#待完善功能清单)
4. [实现步骤详解](#实现步骤详解)
5. [完整代码示例](#完整代码示例)
6. [为什么这样设计](#为什么这样设计)
7. [测试验证](#测试验证)
8. [常见问题](#常见问题)

---

## 技术选型决策

### ✅ 最终方案：Spring AI 官方 + 智谱 AI

| 技术栈 | 版本 | 说明 |
|--------|------|------|
| **Spring AI** | 1.0.0-M4+ | Spring 官方 AI 框架 |
| **智谱 AI** | GLM-4-Flash / GLM-5 | 国产大模型，中文优化 |
| **模型选择** | glm-4-flash | 开发测试（¥0.1/百万 tokens） |
| **生产模型** | glm-4-air | 生产环境（¥0.5/百万 tokens） |

### 为什么选择这个方案？

#### 1. Spring AI 官方 - 智谱原生支持

```xml
<!-- 官方 zhipuai 模块，无需适配层 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-zhipuai</artifactId>
</dependency>
```

**优势**：
- ✅ Spring 官方维护，长期保障
- ✅ 智谱 AI 原生支持，配置简单
- ✅ 不依赖云厂商，无绑定风险
- ✅ 社区活跃，问题容易解决

#### 2. 智谱 AI - 中文简历解析最佳选择

| 特性 | 智谱 AI | 说明 |
|------|---------|------|
| **中文理解** | ⭐⭐⭐⭐⭐ | 专门针对中文优化 |
| **价格** | ¥0.1/百万 tokens | 极具性价比 |
| **国内访问** | ✅ 稳定快速 | 无需代理 |
| **GLM-5** | 2026年2月发布 | 编程能力对标 Claude |

### 相关文档

- **智谱 AI 集成详细指南**：`docs/zhipu-ai-integration-guide.md`
- **技术选型分析**：`docs/spring-ai-vs-spring-ai-alibaba-analysis.md`

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

#### 3. 消息消费者

**文件**: `ResumeParseConsumer.java`

**当前实现**：
- ✅ 幂等性检查（防止重复处理）
- ✅ 手动 ACK 消息确认
- ✅ 失败重试机制（最多3次）
- ✅ Webhook 事件触发
- ⚠️ AI 解析是模拟的（需要实现）

### ⚠️ 待实现的功能

| 优先级 | 功能 | 预计工期 |
|--------|------|---------|
| 🔴 P0 | AI 简历解析服务 | 2天 |
| 🔴 P0 | 候选人模块（数据库+实体） | 2天 |
| 🔴 P0 | Redisson 分布式锁 | 1天 |
| 🟡 P1 | 候选人管理接口（CRUD） | 2天 |
| 🟡 P1 | 简历列表和详情查询 | 1天 |
| 🟢 P2 | 简历删除功能 | 1天 |

---

## 待完善功能清单

### 🔴 高优先级（核心功能）

#### 1. AI 简历解析服务

**需求**：
- 从 MinIO 下载简历文件
- 提取文本内容（PDF/DOC/DOCX）
- 使用智谱 AI 解析内容
- 提取结构化候选人信息

**涉及文件**：
- `ResumeContentExtractor.java` (新建) - 文件内容提取
- `ResumeParseService.java` (新建) - AI 解析服务

#### 2. 候选人管理模块

**需求**：
- 创建 candidates 表（SQL 已提供）
- 创建 Candidate 实体和 Mapper
- 实现候选人 CRUD 接口

**涉及文件**：
- `Candidate.java` (新建)
- `CandidateMapper.java` (新建)
- `CandidateService.java` (新建)
- `CandidateController.java` (新建)

#### 3. Redisson 分布式锁

**需求**：
- 替换现有的简单锁注释
- 防止同一文件被并发解析
- 使用看门狗机制自动续期

**涉及文件**：
- `RedissonConfig.java` (新建)
- `ResumeParseConsumer.java` (修改)

---

## 实现步骤详解

### 阶段一：基础设施准备（1天）

#### 步骤 1.1：添加 Maven 依赖

**pom.xml**：

```xml
<properties>
    <!-- Spring AI 版本 -->
    <spring-ai.version>1.0.0-M4</spring-ai.version>

    <!-- 其他版本 -->
    <redisson.version>3.25.0</redisson.version>
    <poi.version>5.2.5</poi.version>
    <pdfbox.version>2.0.29</pdfbox.version>
</properties>

<dependencies>
    <!-- ========== Spring AI 智谱 AI ========== -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-zhipuai</artifactId>
        <version>${spring-ai.version}</version>
    </dependency>

    <!-- Redisson 分布式锁 -->
    <dependency>
        <groupId>org.redisson</groupId>
        <artifactId>redisson-spring-boot-starter</artifactId>
        <version>${redisson.version}</version>
    </dependency>

    <!-- Apache POI（解析 DOC/DOCX） -->
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>${poi.version}</version>
    </dependency>

    <!-- Apache PDFBox（解析 PDF） -->
    <dependency>
        <groupId>org.apache.pdfbox</groupId>
        <artifactId>pdfbox</artifactId>
        <version>${pdfbox.version}</version>
    </dependency>
</dependencies>

<!-- ========== 添加 Spring Milestone 仓库 ========== -->
<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>
```

#### 步骤 1.2：配置环境变量

**.env 文件**：

```bash
# ========== 智谱 AI 配置 ==========
ZHIPU_API_KEY=你的API密钥
ZHIPU_API_BASE=https://open.bigmodel.cn/api/paas/v4
ZHIPU_MODEL=glm-4-flash

# 模型说明：
# - glm-4-flash：速度快、价格低，适合开发测试（¥0.1/百万 tokens）
# - glm-4-air：性价比高，适合生产环境（¥0.5/百万 tokens）
# - glm-4：能力更强，适合复杂任务（¥1.0/百万 tokens）
# - glm-5：最新旗舰，编程能力最强（2026年2月发布）
```

**获取 API Key**：
1. 访问 [智谱 AI 开放平台](https://open.bigmodel.cn/)
2. 注册并实名认证
3. 在「API Key」页面创建密钥

#### 步骤 1.3：配置 application.yml

```yaml
spring:
  ai:
    zhipuai:
      # 智谱 API Key（从环境变量读取）
      api-key: ${ZHIPU_API_KEY}
      chat:
        enabled: true
        options:
          # 模型选择
          model: ${ZHIPU_MODEL:glm-4-flash}
          # 温度（0-1，简历解析建议 0.3 获得更确定的输出）
          temperature: 0.3
          # 最大 token 数
          max-tokens: 4000
```

#### 步骤 1.4：创建数据库表

```bash
# 执行 SQL 文件
mysql -u smartats -p smartats < src/main/resources/db/candidates_table.sql
```

**SQL 文件位置**：`src/main/resources/db/candidates_table.sql`

---

### 阶段二：候选人模块（2天）

#### 步骤 2.1：创建 Candidate 实体

**文件位置**：`src/main/java/com/smartats/module/candidate/entity/Candidate.java`

**关键点**：
- 使用 `@TableName` 指定表名
- JSON 字段使用 `@TableField(typeHandler = JsonTypeHandler.class)`
- 关联简历 ID 设置唯一索引

#### 步骤 2.2：创建 JSON 类型处理器

**文件位置**：`src/main/java/com/smartats/common/handler/JsonTypeHandler.java`

**功能**：
- 写入数据库：Java List/Map → JSON 字符串
- 读取数据库：JSON 字符串 → Java List/Map

#### 步骤 2.3：创建 CandidateMapper 和 Service

**文件位置**：
- `src/main/java/com/smartats/module/candidate/mapper/CandidateMapper.java`
- `src/main/java/com/smartats/module/candidate/service/CandidateService.java`

---

### 阶段三：AI 解析服务（2天）

#### 步骤 3.1：创建 Spring AI 配置

**文件位置**：`src/main/java/com/smartats/config/ZhipuAiConfig.java`

#### 步骤 3.2：创建文件内容提取服务

**文件位置**：`src/main/java/com/smartats/module/resume/service/ResumeContentExtractor.java`

**功能**：
- 从 MinIO 下载文件
- 根据文件类型提取文本：
  - PDF：使用 Apache PDFBox
  - DOCX：使用 Apache POI (XWPF)
  - DOC：使用 Apache POI (HWPF)

#### 步骤 3.3：创建 AI 解析服务

**文件位置**：`src/main/java/com/smartats/module/resume/service/ResumeParseService.java`

**功能**：
- 接收纯文本简历内容
- 构建 Prompt（针对中文简历优化）
- 调用智谱 AI
- 返回 CandidateInfo 对象

---

### 阶段四：集成到消费者（1天）

#### 步骤 4.1：修改 ResumeParseConsumer

**修改点**：
1. 引入 `ResumeParseService` 和 `CandidateService`
2. 使用 Redisson 替换 TODO 注释
3. 调用实际解析逻辑替换 `Thread.sleep(3000)`

---

### 阶段五：候选人管理接口（2天）

#### 步骤 5.1：创建 CandidateController

**接口列表**：
- `GET /candidates` - 查询列表（分页、筛选）
- `GET /candidates/{id}` - 查询详情
- `PUT /candidates/{id}` - 更新（手动修正）
- `DELETE /candidates/{id}` - 删除

---

## 完整代码示例

> **重要提示**：以下代码示例仅供参考，请理解设计思路后自行实现。

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
    private String name;
    private String phone;
    private String email;
    private String gender;
    private Integer age;

    // ========== 教育信息 ==========
    private String education;
    private String school;
    private String major;
    private Integer graduationYear;

    // ========== 工作信息 ==========
    private Integer workYears;
    private String currentCompany;
    private String currentPosition;

    // ========== JSON 字段 ==========
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<String> skills;

    @TableField(typeHandler = JsonTypeHandler.class)
    private List<Map<String, Object>> workExperience;

    @TableField(typeHandler = JsonTypeHandler.class)
    private List<Map<String, Object>> projectExperience;

    private String selfEvaluation;

    // ========== AI 解析元数据 ==========
    private String rawJson;
    private Double confidenceScore;
    private LocalDateTime parsedAt;

    // ========== 审计字段 ==========
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### 示例 2：JsonTypeHandler

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

/**
 * JSON 类型处理器
 * 用于 MyBatis-Plus JSON 字段的序列化/反序列化
 */
@Slf4j
@MappedTypes({List.class, Map.class})
public class JsonTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        try {
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

### 示例 3：CandidateInfo DTO

```java
package com.smartats.module.resume.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 解析的候选人信息 DTO
 * 与智谱 AI 返回的 JSON 结构对应
 */
@Data
public class CandidateInfo {

    // 基本信息
    private String name;
    private String phone;
    private String email;
    private String gender;
    private Integer age;

    // 教育信息
    private String education;
    private String school;
    private String major;
    private Integer graduationYear;

    // 工作信息
    private Integer workYears;
    private String currentCompany;
    private String currentPosition;

    // 技能与经历
    private List<String> skills;
    private List<WorkExperience> workExperience;
    private List<ProjectExperience> projectExperience;
    private String selfEvaluation;

    @Data
    public static class WorkExperience {
        private String company;
        private String position;
        private String startDate;
        private String endDate;
        private String description;
    }

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

### 示例 4：ZhipuAiConfig 配置

```java
package com.smartats.config;

import org.springframework.ai.zhipuai.ZhipuAiChatModel;
import org.springframework.ai.zhipuai.ZhipuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智谱 AI 配置
 * 使用 Spring AI 官方 zhipuai 模块
 */
@Configuration
public class ZhipuAiConfig {

    @Value("${spring.ai.zhipuai.api-key}")
    private String apiKey;

    @Value("${spring.ai.zhipuai.chat.options.model:glm-4-flash}")
    private String model;

    @Value("${spring.ai.zhipuai.chat.options.temperature:0.3}")
    private Double temperature;

    @Bean
    public ZhipuAiChatModel zhipuAiChatModel() {
        ZhipuAiApi api = new ZhipuAiApi(apiKey);

        ZhipuAiChatOptions options = ZhipuAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxTokens(4000)
                .build();

        return new ZhipuAiChatModel(api, options);
    }
}
```

### 示例 5：文件内容提取服务

```java
package com.smartats.module.resume.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
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

/**
 * 简历内容提取服务
 * 从 PDF/DOC/DOCX 中提取纯文本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeContentExtractor {

    /**
     * 从文件 URL 提取文本内容
     */
    public String extractText(String fileUrl, String fileType) {
        log.info("开始提取文件内容: fileUrl={}, fileType={}", fileUrl, fileType);

        try {
            URL url = new URL(fileUrl);
            InputStream inputStream = url.openStream();

            String text = switch (fileType) {
                case "application/pdf" -> extractFromPDF(inputStream);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        -> extractFromDOCX(inputStream);
                case "application/msword" -> extractFromDOC(inputStream);
                default -> throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的文件类型");
            };

            inputStream.close();
            return text;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件内容提取失败: fileUrl={}", fileUrl, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件解析失败");
        }
    }

    private String extractFromPDF(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractFromDOCX(InputStream inputStream) throws Exception {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
        }
        return text.toString();
    }

    private String extractFromDOC(InputStream inputStream) throws Exception {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }
}
```

### 示例 6：AI 解析服务（智谱 AI）

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
 * 简历 AI 解析服务（智谱 AI）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParseService {

    private final ChatClient chatClient;

    @Value("${spring.ai.zhipuai.chat.options.model:glm-4-flash}")
    private String model;

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
            Prompt aiPrompt = new Prompt(new UserMessage(prompt));
            ChatResponse response = chatClient.call(aiPrompt);

            String responseContent = response.getResult().getOutput().getContent();

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

    private String buildPromptForChineseResume(String resumeContent, String formatInstructions) {
        return """
                你是一个专业的简历信息提取助手。请从以下中文简历内容中提取结构化信息，并以 JSON 格式返回。

                ## 提取字段说明

                ### 基本信息
                - name: 姓名
                - phone: 手机号（11位数字）
                - email: 邮箱地址
                - gender: 性别（男/女）
                - age: 年龄

                ### 教育信息
                - education: 学历（高中/专科/本科/硕士研究生/博士研究生）
                - school: 毕业院校
                - major: 专业
                - graduationYear: 毕业年份（4位整数）

                ### 工作信息
                - workYears: 工作年限（整数年）
                - currentCompany: 当前公司
                - currentPosition: 当前职位

                ### 技能与经历
                - skills: 技能列表（只保留技术技能）
                - workExperience: 工作经历数组
                - projectExperience: 项目经历数组
                - selfEvaluation: 自我评价

                ## 日期格式转换

                - "2020年1月" → "2020-01"
                - "2020.01" → "2020-01"
                - "至今" → "至今"

                ## 注意事项

                1. 无法提取的字段使用 null
                2. 日期格式统一为 yyyy-MM
                3. 技能列表只保留核心技术
                4. 工作经历按时间倒序
                5. 只返回 JSON，不包含 markdown 代码块标记

                ## 输出格式

                %s

                ## 简历内容

                %s

                请返回提取的 JSON：
                """.formatted(formatInstructions, resumeContent);
    }
}
```

### 示例 7：RedissonConfig 配置

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
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        String address = "redis://" + redisHost + ":" + redisPort;

        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setConnectionPoolSize(20)
                .setConnectionMinimumIdleSize(5)
                .setLockWatchdogTimeout(30000)  // 30秒看门狗
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }
}
```

### 示例 8：修改后的 ResumeParseConsumer

```java
package com.smartats.module.resume.consumer;

import com.rabbitmq.client.Channel;
import com.smartats.module.resume.service.ResumeContentExtractor;
import com.smartats.module.resume.service.ResumeParseService;
// ... 其他导入

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParseConsumer {

    // 新增依赖
    private final ResumeContentExtractor contentExtractor;
    private final ResumeParseService parseService;
    private final CandidateService candidateService;
    private final RedissonClient redissonClient;

    @RabbitListener(queues = RabbitMQConfig.RESUME_PARSE_QUEUE)
    public void consumeResumeParse(
            ResumeParseMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {
        String taskId = message.getTaskId();
        Long resumeId = message.getResumeId();
        String fileHash = message.getFileHash();

        // 1. 幂等检查
        // 2. 获取分布式锁（Redisson）
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + fileHash);

        try {
            boolean acquired = lock.tryLock(10, 300, TimeUnit.SECONDS);
            if (!acquired) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 更新状态
            updateTaskStatus(taskId, "PROCESSING", 10);

            // 4. 查询简历
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume == null) {
                updateTaskStatus(taskId, "FAILED", 0, "简历不存在");
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 5. 提取文件内容
            String content = contentExtractor.extractText(resume.getFileUrl(), resume.getFileType());
            updateTaskStatus(taskId, "PROCESSING", 30);

            // 6. AI 解析
            CandidateInfo candidateInfo = parseService.parseResume(content);
            updateTaskStatus(taskId, "PROCESSING", 70);

            // 7. 保存候选人信息
            Candidate candidate = candidateService.createCandidate(resumeId, candidateInfo);
            updateTaskStatus(taskId, "PROCESSING", 90);

            // 8. 更新状态
            updateTaskStatus(taskId, "COMPLETED", 100);

            // 9. 触发 Webhook
            triggerWebhookEvent(WebhookEventType.RESUME_PARSE_COMPLETED, resume, taskId, null, candidate);

            // 10. ACK
            channel.basicAck(deliveryTag, false);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

---

## 为什么这样设计

### 1. 为什么选择 Spring AI 官方 + 智谱 AI

| 优势 | 说明 |
|------|------|
| **原生支持** | Spring AI 官方提供 zhipuai 模块，无需适配层 |
| **配置简单** | 3 行配置即可完成集成 |
| **长期维护** | Spring 官方维护，不依赖云厂商 |
| **中文优化** | 智谱 GLM 专门针对中文优化 |
| **价格优势** | GLM-4-Flash 仅 ¥0.1/百万 tokens |

### 2. 为什么使用异步架构

- 用户上传 → 立即返回 taskId（不等待 AI 解析）
- 后台异步处理 → 更新 Redis 状态
- 前端轮询查询 → 获取解析结果

### 3. 为什么 candidates 和 resumes 分表

- `resumes`：文件元数据（文件名、大小、路径）
- `candidates`：结构化候选人信息（姓名、工作经历）

好处：职责分离、扩展性强、查询性能高

### 4. 为什么使用 JSON 字段

- 工作经历、项目经历结构复杂
- MySQL 5.7+ 支持 JSON 索引和查询
- 灵活性高，不需要额外表

---

## 测试验证

### 单元测试示例

```java
@SpringBootTest
class ResumeParseServiceTest {

    @Autowired
    private ResumeParseService parseService;

    @Test
    void testParseResume() {
        String content = """
                张三
                13800138000
                zhangsan@example.com

                工作经验：5年
                学历：本科
                """;

        CandidateInfo info = parseService.parseResume(content);

        assertNotNull(info);
        assertEquals("张三", info.getName());
    }
}
```

### 集成测试示例

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
                "file", "resume.pdf",
                "application/pdf",
                Files.readAllBytes(Path.of("test-resume.pdf"))
        );

        // 2. 获取 taskId
        String response = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 3. 轮询查询状态
        await().atMost(30, SECONDS).until(() -> {
            String status = mockMvc.perform(get("/api/v1/resumes/tasks/" + taskId))
                    .andReturn().getResponse().getContentAsString();

            return status.contains("COMPLETED") || status.contains("FAILED");
        });
    }
}
```

---

## 常见问题

### Q1: 智谱 API 调用失败？

**解决方案**：
1. 检查 API Key 是否正确
2. 确认账户余额（新用户有免费额度）
3. 查看控制台日志确认错误信息

### Q2: 返回结果不是纯 JSON？

**解决方案**：智谱 AI 可能返回 markdown 代码块，需要在解析前清理：

```java
String content = response.trim();
if (content.startsWith("```json")) {
    content = content.substring(7);
}
if (content.startsWith("```")) {
    content = content.substring(3);
}
if (content.endsWith("```")) {
    content = content.substring(0, content.length() - 3);
}
```

### Q3: Redisson 配置报错？

**解决方案**：确认 Redis 已启动，密码配置正确

### Q4: 成本估算？

**1000 份简历成本**：
- GLM-4-Flash：约 ¥0.35
- GLM-4-Air：约 ¥1.75

---

## 下一步计划

完成上述功能后，可以继续开发：

1. **向量搜索（RAG）**
   - 候选人信息向量化
   - 语义搜索

2. **智能推荐**
   - 根据职位推荐候选人
   - 计算匹配度分数

3. **批量操作**
   - 批量上传
   - 批量导出

---

**开始开发吧！** 🚀

如有问题，请参考：
- `docs/zhipu-ai-integration-guide.md` - 智谱 AI 详细指南
- `docs/spring-ai-vs-spring-ai-alibaba-analysis.md` - 技术选型分析
