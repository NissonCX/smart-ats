# 智谱 AI 集成指南

**文档版本**: v1.0
**创建日期**: 2026-02-20
**API 提供商**: 智谱 AI (Zhipu AI / bigmodel.cn)

---

## 📋 目录

1. [智谱 AI 简介](#智谱-ai-简介)
2. [API 密钥获取](#api-密钥获取)
3. [项目配置](#项目配置)
4. [代码实现](#代码实现)
5. [Prompt 优化建议](#prompt-优化建议)
6. [常见问题](#常见问题)

---

## 智谱 AI 简介

### 为什么选择智谱 AI

| 特性 | 智谱 AI | OpenAI |
|------|---------|--------|
| **国内访问** | ✅ 稳定快速 | ❌ 需要代理 |
| **中文理解** | ✅ 专门优化 | ⚠️ 一般 |
| **价格** | 💰 更便宜 | 💰💰 较贵 |
| **API 格式** | ✅ 兼容 OpenAI | ✅ 标准 |
| **结构化输出** | ✅ 支持 | ✅ 支持 |

### 智谱 AI 模型对比

| 模型 | 特点 | 适用场景 | 价格（约） |
|------|------|----------|-----------|
| **GLM-4-Flash** | 速度快、价格低 | 简单任务、测试 | ¥0.1/百万 tokens |
| **GLM-4-Air** | 性价比高 | 日常开发 | ¥0.5/百万 tokens |
| **GLM-4** | 综合能力强 | 生产环境推荐 | ¥1.0/百万 tokens |
| **GLM-4-Plus** | 最强能力 | 复杂任务 | ¥2.0/百万 tokens |

**推荐选择**：
- 开发测试：`GLM-4-Flash`（便宜快速）
- 生产环境：`GLM-4` 或 `GLM-4-Air`（性价比高）

---

## API 密钥获取

### 步骤 1：注册账号

1. 访问 [智谱 AI 开放平台](https://open.bigmodel.cn/)
2. 点击右上角「注册」
3. 使用手机号注册

### 步骤 2：实名认证

1. 登录后进入「控制台」
2. 完成企业/个人实名认证
3. 充值（新用户有免费额度）

### 步骤 3：创建 API Key

1. 进入「API Key」页面
2. 点击「新建 API Key」
3. 复制生成的 Key（格式：`xxxxxxxxxxxxx.xxxxxxxxxxxxx`）

### 步骤 4：测试 API Key

```bash
curl -X POST https://open.bigmodel.cn/api/paas/v4/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{
    "model": "glm-4-flash",
    "messages": [{"role": "user", "content": "你好"}]
  }'
```

---

## 项目配置

### 1. 添加 Maven 依赖

在 `pom.xml` 中添加：

```xml
<!-- Spring AI OpenAI（智谱兼容 OpenAI 格式）-->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M4</version>
</dependency>

<!-- 如果 Spring AI 不支持，使用直接 HTTP 调用 -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

### 2. 配置环境变量

在 `.env` 文件中添加：

```bash
# 智谱 AI 配置
ZHIPU_API_KEY=xxxxxxxxxxxxx.xxxxxxxxxxxxx
ZHIPU_API_BASE=https://open.bigmodel.cn/api/paas/v4
ZHIPU_MODEL=glm-4-flash
```

### 3. 配置 application.yml

```yaml
spring:
  ai:
    openai:
      # 智谱 API Key
      api-key: ${ZHIPU_API_KEY}
      # 智谱 API 地址
      base-url: ${ZHIPU_API_BASE:https://open.bigmodel.cn/api/paas/v4}
      chat:
        options:
          # 模型选择
          model: ${ZHIPU_MODEL:glm-4-flash}
          # 温度（0-1，越高越随机）
          temperature: 0.3
          # 最大 token 数
          max-tokens: 4000
```

---

## 代码实现

### 方案一：使用 Spring AI（推荐）

#### SpringAIConfig.java

```java
package com.smartats.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智谱 AI 配置
 * <p>
 * 说明：智谱 AI 兼容 OpenAI API 格式，可以直接使用 Spring AI
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

    @Bean
    public OpenAiChatModel openAiChatModel() {
        // 创建智谱 API 客户端
        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);

        // 配置选项
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)           // glm-4-flash / glm-4-air / glm-4
                .withTemperature(temperature)  // 0.3（较低温度，更确定的输出）
                .withMaxTokens(4000)        // 最大输出长度
                .build();

        return new OpenAiChatModel(openAiApi, options);
    }
}
```

#### ResumeParseService.java（完整版）

```java
package com.smartats.module.resume.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.resume.dto.CandidateInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
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
     * 3. 添加中文简历常见格式示例
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

### 方案二：使用 OkHttp 直接调用（备选）

如果 Spring AI 兼容性有问题，可以使用 OkHttp 直接调用：

```java
package com.smartats.module.resume.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.resume.dto.CandidateInfo;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 简历 AI 解析服务（智谱 AI - OkHttp 实现）
 * <p>
 * 备选方案：如果 Spring AI 兼容性问题，使用此方案
 */
@Slf4j
@Service
public class ZhipuResumeParseService {

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    @Value("${zhipu.api-key}")
    private String apiKey;

    @Value("${zhipu.api-base:https://open.bigmodel.cn/api/paas/v4}")
    private String apiBase;

    @Value("${zhipu.model:glm-4-flash}")
    private String model;

    public ZhipuResumeParseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 解析简历内容
     */
    public CandidateInfo parseResume(String resumeContent) {
        log.info("开始使用智谱 AI 解析简历: model={}", model);

        try {
            // 1. 构建 Request Body
            String requestBody = buildRequestBody(resumeContent);

            // 2. 创建 HTTP Request
            Request request = new Request.Builder()
                    .url(apiBase + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            // 3. 发送请求
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("智谱 API 调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                log.debug("智谱 AI 响应: {}", responseBody);

                // 4. 解析响应
                return parseResponse(responseBody);
            }

        } catch (Exception e) {
            log.error("智谱 AI 解析失败", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "简历解析失败: " + e.getMessage());
        }
    }

    /**
     * 构建请求体
     */
    private String buildRequestBody(String resumeContent) {
        String prompt = buildPromptForChineseResume(resumeContent);

        try {
            JsonNode requestBody = objectMapper.createObjectNode()
                    .put("model", model)
                    .set("messages", objectMapper.createArrayNode()
                            .add(objectMapper.createObjectNode()
                                    .put("role", "user")
                                    .put("content", prompt)))
                    .put("temperature", 0.3);

            return objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    /**
     * 解析响应
     */
    private CandidateInfo parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            // 去除可能的 markdown 代码块标记
            content = content.trim();
            if (content.startsWith("```json")) {
                content = content.substring(7);
            }
            if (content.startsWith("```")) {
                content = content.substring(3);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
            content = content.trim();

            return objectMapper.readValue(content, CandidateInfo.class);
        } catch (Exception e) {
            log.error("解析智谱 AI 响应失败: responseBody={}", responseBody, e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "解析 AI 响应失败");
        }
    }

    /**
     * 构建 Prompt（同上）
     */
    private String buildPromptForChineseResume(String resumeContent) {
        return String.format("""
                你是一个专业的简历信息提取助手。请从以下中文简历内容中提取结构化信息，并以 JSON 格式返回。

                ## 提取字段说明

                ### 基本信息
                - name: 姓名
                - phone: 手机号（11位数字）
                - email: 邮箱地址
                - gender: 性别（男/女）
                - age: 年龄

                ### 教育信息
                - education: 学历
                - school: 毕业院校
                - major: 专业
                - graduationYear: 毕业年份

                ### 工作信息
                - workYears: 工作年限
                - currentCompany: 当前公司
                - currentPosition: 当前职位

                ### 技能与经历
                - skills: 技能列表（数组）
                - workExperience: 工作经历数组
                - projectExperience: 项目经历数组
                - selfEvaluation: 自我评价

                ## 输出格式

                请以以下 JSON 格式返回：
                {
                  "name": "张三",
                  "phone": "13800138000",
                  "email": "zhangsan@example.com",
                  "gender": "男",
                  "age": 28,
                  "education": "本科",
                  "school": "清华大学",
                  "major": "计算机科学与技术",
                  "graduationYear": 2020,
                  "workYears": 5,
                  "currentCompany": "腾讯科技",
                  "currentPosition": "后端开发工程师",
                  "skills": ["Java", "Spring Boot", "MySQL", "Redis"],
                  "workExperience": [
                    {
                      "company": "腾讯科技",
                      "position": "后端开发工程师",
                      "startDate": "2020-01",
                      "endDate": "2023-06",
                      "description": "负责核心业务系统开发"
                    }
                  ],
                  "projectExperience": [],
                  "selfEvaluation": "5年后端开发经验"
                }

                ## 简历内容

                %s

                请提取并返回 JSON：
                """, resumeContent);
    }
}
```

---

## Prompt 优化建议

### 1. 针对中文简历的优化

#### 问题：中文简历格式多样

中文简历可能有以下格式：
- 时间格式：`2020年1月`、`2020.01`、`2020/01`、`2020-01`
- 学历表达：`本科`、`学士`、`大学本科`
- 公司表达：可能包含分公司信息

#### 优化方案：

```java
// 在 Prompt 中添加明确的格式说明

"日期格式转换规则：
- '2020年1月' → '2020-01'
- '2020.01' → '2020-01'
- '2020/01' → '2020-01'
- '2020年01月至今' → '2020-01'，endDate设为'至今'

学历标准化：
- '大学本科'、'本科'、'学士' → '本科'
- '硕士研究生'、'硕士'、'研究生硕士' → '硕士研究生'
- '博士研究生'、'博士' → '博士研究生'

职位标准化：
- 'Java开发'、'Java工程师' → 'Java开发工程师'
- '后端'、'后端开发' → '后端开发工程师'"
```

### 2. 提高提取准确率

#### 技巧 1：Few-Shot Learning（少样本学习）

在 Prompt 中提供示例：

```java
String prompt = """
    你是一个专业的简历信息提取助手。以下是几个提取示例：

    ## 示例 1

    简历内容：
    张三
    13800138000 | zhangsan@example.com
    工作经验：5年
    ...

    提取结果：
    {
      "name": "张三",
      "phone": "13800138000",
      "email": "zhangsan@example.com",
      "workYears": 5
    }

    ## 示例 2
    ...

    ## 现在请提取以下简历：

    %s
    """;
```

#### 技巧 2：思维链（Chain of Thought）

引导 AI 逐步分析：

```java
String prompt = """
    请按以下步骤分析简历：

    1. 首先识别姓名（通常在开头）
    2. 然后找出联系方式（手机和邮箱）
    3. 接着提取教育信息（学校、专业、学历）
    4. 然后分析工作经历（按时间倒序）
    5. 最后提取技能和项目经历

    简历内容：%s

    请以 JSON 格式返回提取结果：
    """;
```

#### 技巧 3：验证和修正

让 AI 自我检查：

```java
String prompt = """
    提取简历信息后，请进行以下检查：

    1. 手机号是否为11位数字
    2. 邮箱格式是否正确
    3. 日期格式是否统一为 yyyy-MM
    4. 工作年限计算是否正确

    简历内容：%s

    请返回检查后的 JSON 结果：
    """;
```

### 3. 实战 Prompt 模板

```java
private String buildOptimizedPrompt(String resumeContent) {
    return """
        你是简历信息提取专家。请从以下中文简历中提取信息。

        ## 提取规则

        ### 1. 基本信息提取
        - 姓名：简历开头通常标注
        - 手机：11位数字，可能包含区号或分隔符
        - 邮箱：标准 email 格式
        - 性别：男/女（从照片、称谓判断，不确定则为 null）
        - 年龄：从出生年份或工作经历推算

        ### 2. 教育信息提取
        - 学历标准化：高中/专科/本科/硕士研究生/博士研究生/MBA
        - 毕业院校：使用全称，如"清华大学"而非"清华"
        - 毕业年份：4位数字

        ### 3. 工作经历提取
        - 按时间倒序排列
        - 时间格式统一：yyyy-MM
        - 如果只写年份，默认为该年1月：yyyy → yyyy-01
        - "至今"或"到现在" → "至今"
        - 公司名称保留完整，包括分公司信息

        ### 4. 技能提取
        - 只保留技术技能：编程语言、框架、数据库、中间件等
        - 过滤通用技能：办公软件、英语水平、沟通能力等
        - 合并相似技能：Spring Boot 和 Spring Cloud → Spring 全家桶

        ### 5. 项目经历提取
        - 提取项目名称、角色、时间、技术栈
        - 技术栈以关键词数组形式返回
        - 项目描述保留关键信息

        ### 6. 输出格式

        ```json
        {
          "name": "姓名",
          "phone": "13800138000",
          "email": "example@qq.com",
          "gender": "男",
          "age": 28,
          "education": "本科",
          "school": "清华大学",
          "major": "计算机科学与技术",
          "graduationYear": 2020,
          "workYears": 5,
          "currentCompany": "腾讯科技（深圳）有限公司",
          "currentPosition": "后端开发工程师",
          "skills": ["Java", "Spring Boot", "MySQL", "Redis", "RabbitMQ"],
          "workExperience": [
            {
              "company": "腾讯科技（深圳）有限公司",
              "position": "后端开发工程师",
              "startDate": "2020-07",
              "endDate": "至今",
              "description": "负责微信支付核心系统开发"
            }
          ],
          "projectExperience": [
            {
              "name": "微服务架构重构",
              "role": "核心开发",
              "startDate": "2022-03",
              "endDate": "2022-12",
              "description": "将单体应用重构为微服务架构",
              "technologies": ["Spring Cloud", "Docker", "Kubernetes"]
            }
          ],
          "selfEvaluation": "5年Java开发经验，熟悉高并发系统设计"
        }
        ```

        ### 7. 质量检查

        提取完成后，请检查：
- [ ] 手机号是否11位
- [ ] 邮箱格式是否正确
- [ ] 日期格式统一为 yyyy-MM
- [ ] 公司名称完整
- [ ] 技能列表只包含技术技能
- [ ] 工作经历按时间倒序

        ## 简历内容

        %s

        请返回提取的 JSON（不包含代码块标记）：
        """.formatted(resumeContent);
}
```

---

## 常见问题

### Q1: Spring AI 报错 "Authentication failed"

**原因**：智谱 API Key 格式特殊，包含 `.`

**解决方案**：

```yaml
# application.yml
spring:
  ai:
    openai:
      # 智谱 API Key 格式：id.secret
      # 需要完整传入，不要处理
      api-key: ${ZHIPU_API_KEY}
```

### Q2: 返回结果不是纯 JSON

**原因**：智谱 AI 可能返回 Markdown 代码块

**解决方案**：

```java
// 解析前清理响应
String content = response.trim();

// 移除 ```json 和 ```
if (content.startsWith("```json")) {
    content = content.substring(7);
}
if (content.startsWith("```")) {
    content = content.substring(3);
}
if (content.endsWith("```")) {
    content = content.substring(0, content.length() - 3);
}

content = content.trim();
```

### Q3: 调用超时

**原因**：简历内容过长，AI 处理时间长

**解决方案**：

```yaml
# application.yml
spring:
  ai:
    openai:
      chat:
        options:
          # 增加超时时间
          max-tokens: 8000
```

```java
// OkHttp 客户端配置
OkHttpClient client = new OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)  // 2分钟
        .build();
```

### Q4: API 配额用尽

**原因**：免费额度有限

**解决方案**：

1. 控制台充值
2. 使用更便宜的模型（GLM-4-Flash）
3. 优化 Prompt，减少 token 消耗

### Q5: 中文识别不准确

**原因**：Prompt 不够针对中文

**解决方案**：

1. 使用中文 Prompt
2. 提供中文示例
3. 针对中文格式转换做说明
4. 使用针对中文优化的模型（GLM 系列）

---

## 性能优化

### 1. Token 优化

简历内容通常很长（2000-5000 字符），需要优化：

```java
/**
 * 预处理简历内容，去除冗余信息
 */
private String preprocessResume(String rawContent) {
    // 1. 去除多余空白
    String content = rawContent.replaceAll("\\s+", " ");

    // 2. 如果内容过长，截取关键部分
    if (content.length() > 8000) {
        // 通常个人信息在前 2000 字符
        // 工作经历在中间
        // 取前 8000 字符通常足够
        content = content.substring(0, 8000) + "...";
    }

    return content;
}
```

### 2. 缓存优化

```java
/**
 * 缓存 AI 解析结果
 */
@Cacheable(value = "resume:parse", key = "#resumeHash", unless = "#result == null")
public CandidateInfo parseResumeWithCache(String resumeContent, String resumeHash) {
    return parseResume(resumeContent);
}
```

### 3. 异步处理

```java
@Async("aiParseExecutor")
public CompletableFuture<CandidateInfo> parseResumeAsync(String resumeContent) {
    return CompletableFuture.completedFuture(parseResume(resumeContent));
}
```

---

## 成本估算

### 智谱 AI 定价（2024年）

| 模型 | 输入 | 输出 |
|------|------|------|
| GLM-4-Flash | ¥0.1/百万 tokens | ¥0.1/百万 tokens |
| GLM-4-Air | ¥0.5/百万 tokens | ¥0.5/百万 tokens |
| GLM-4 | ¥1.0/百万 tokens | ¥1.0/百万 tokens |

### 单次简历解析成本

假设：
- 简历内容：3000 字符 ≈ 1500 tokens
- Prompt：2000 字符 ≈ 1000 tokens
- 输出：1000 字符 ≈ 500 tokens

总计：3000 tokens（输入）+ 500 tokens（输出）= 3500 tokens

**成本计算**：
- GLM-4-Flash：3500 / 100万 × ¥0.1 ≈ ¥0.00035（约 0.00035 元）
- GLM-4：3500 / 100万 × ¥1.0 ≈ ¥0.0035（约 0.0035 元）

**1000 份简历成本**：
- GLM-4-Flash：约 ¥0.35
- GLM-4：约 ¥3.5

---

## 下一步

1. **测试环境配置**
   ```bash
   # 在 .env 中配置
   ZHIPU_API_KEY=your_api_key_here
   ZHIPU_MODEL=glm-4-flash  # 先用便宜的测试
   ```

2. **执行数据库脚本**
   ```bash
   mysql -u smartats -p smartats < src/main/resources/db/candidates_table.sql
   ```

3. **按照开发手册实现其他模块**
   - 参考 `docs/resume-module-enhancement-guide.md`

---

## 参考资料

- [智谱 AI 官方文档](https://open.bigmodel.cn/dev/api)
- [智谱 AI 定价](https://open.bigmodel.cn/pricing)
- [Spring AI 文档](https://docs.spring.io/spring-ai/reference/)
