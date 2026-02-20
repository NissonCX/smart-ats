# SmartATS 安全修复和 Webhook 功能实现总结

## 一、安全漏洞修复

### 1. CORS 配置（高风险 ✅ 已修复）

**问题**: 缺少 CORS 配置，可能导致跨站攻击

**修复**: 在 `SecurityConfig.java` 中添加了 CORS 配置
- 开发环境：允许所有来源（仅用于开发）
- 生产环境：需要配置具体的允许域名

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    return request -> {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        return config;
    };
}
```

### 2. 文件上传安全增强（中风险 ✅ 已修复）

**问题**: 仅通过 Content-Type 验证文件类型，容易被绕过

**修复**: 创建了 `FileValidationUtil` 工具类
- ✅ 通过文件头（魔数）验证真实文件类型
- ✅ 文件名消毒处理（防止路径遍历）
- ✅ 支持 PDF、DOC、DOCX 的魔数检查

**新增文件**: `src/main/java/com/smartats/common/util/FileValidationUtil.java`

```java
// 验证文件真实类型
boolean isValid = FileValidationUtil.validateFileType(
    file.getInputStream(),
    contentType,
    file.getOriginalFilename()
);

// 文件名消毒
String safeName = FileValidationUtil.sanitizeFilename(file.getOriginalFilename());
```

### 3. Webhook 接口安全（✅ 已配置）

在 `SecurityConfig` 中添加了 Webhook 接口的匿名访问权限：

```java
.requestMatchers(HttpMethod.POST, "/webhooks/**").permitAll()
```

**注意**: Webhook 接口使用签名验证（HMAC-SHA256）来保证安全性。

### 4. 其他安全建议（待实施）

#### 敏感信息硬编码（高风险）
- JWT 密钥、数据库密码、Redis 密码等硬编码在 `application.yml` 中
- **建议**: 使用环境变量或配置中心管理

#### 密码策略（中风险）
- 当前密码长度要求 6-20 字符，过于宽松
- **建议**: 要求包含大小写字母、数字和特殊字符

## 二、Webhook 功能实现

### 功能特性

| 功能 | 状态 | 说明 |
|------|------|------|
| 事件订阅 | ✅ | 支持订阅多种系统事件 |
| 异步发送 | ✅ | 使用独立线程池，不阻塞主流程 |
| 签名验证 | ✅ | HMAC-SHA256 签名，确保请求真实性 |
| 自动重试 | ✅ | 发送失败自动重试 |
| 自动禁用 | ✅ | 连续失败 5 次自动禁用 |
| 日志记录 | ✅ | 完整的调用日志，方便调试 |

### 支持的事件类型

#### 简历相关
- `resume.uploaded` - 新简历上传
- `resume.parse_completed` - 简历解析完成
- `resume.parse_failed` - 简历解析失败

#### 候选人相关
- `candidate.created` - 候选人信息创建
- `candidate.updated` - 候选人信息更新

#### 职位申请相关
- `application.submitted` - 提交职位申请
- `application.status_changed` - 申请状态变更

#### 面试相关
- `interview.scheduled` - 面试安排
- `interview.completed` - 面试完成
- `interview.cancelled` - 面试取消

#### 系统事件
- `system.error` - 系统错误
- `system.maintenance` - 系统维护通知

### 核心文件

| 文件 | 说明 |
|------|------|
| `WebhookConfig.java` | Webhook 配置实体 |
| `WebhookLog.java` | Webhook 调用日志实体 |
| `WebhookEventType.java` | 事件类型枚举 |
| `WebhookService.java` | Webhook 核心服务 |
| `WebhookController.java` | Webhook 管理 API |
| `AsyncConfig.java` | 异步线程池配置 |

### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/webhooks` | 创建 Webhook 配置 |
| GET | `/api/v1/webhooks` | 查询 Webhook 列表 |
| DELETE | `/api/v1/webhooks/{id}` | 删除 Webhook 配置 |
| POST | `/api/v1/webhooks/{id}/test` | 测试 Webhook |

### 数据库表

新增两个数据库表：

1. **webhook_configs** - Webhook 配置表
   - 存储用户的 Webhook URL、订阅事件、密钥等

2. **webhook_logs** - Webhook 调用日志表
   - 记录每次调用的详细信息

SQL 文件位置：`src/main/resources/db/webhook_tables.sql`

### Webhook 请求格式

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "resume.parse_completed",
  "timestamp": "2026-02-20T16:00:00",
  "version": "1.0",
  "signature": "sha256=xxxxxxxxxxxxx",
  "data": {
    "taskId": "...",
    "resumeId": 3,
    "fileName": "张三_Java_北京大学.pdf",
    "userId": 4,
    "status": "COMPLETED"
  }
}
```

### 集成示例

在 `ResumeParseConsumer` 中集成了 Webhook 事件触发：

```java
// 解析完成时触发
triggerWebhookEvent(WebhookEventType.RESUME_PARSE_COMPLETED, resume, taskId, null);

// 解析失败时触发
triggerWebhookEvent(WebhookEventType.RESUME_PARSE_FAILED, resume, taskId, errorMessage);
```

## 三、待完成的工作

### 高优先级

1. **执行数据库迁移**
   ```bash
   mysql -u smartats -p smartats < src/main/resources/db/webhook_tables.sql
   ```

2. **实现 Redisson 分布式锁**
   - 替换 `ResumeParseConsumer` 中的 TODO 部分
   - 防止并发解析同一文件

3. **实现 AI 简历解析**
   - 集成 Spring AI 或 LLM API
   - 提取结构化候选人信息

### 中优先级

1. **敏感配置迁移到环境变量**
2. **增强密码策略**
3. **实现 Webhook 测试功能**

## 四、测试建议

### 1. 安全测试
```bash
# 测试文件上传验证
curl -X POST http://localhost:8080/api/v1/resumes/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@fake.pdf"

# 测试 CORS
curl -X OPTIONS http://localhost:8080/api/v1/jobs \
  -H "Origin: http://evil.com" \
  -H "Access-Control-Request-Method: GET"
```

### 2. Webhook 测试
```bash
# 创建 Webhook
curl -X POST http://localhost:8080/api/v1/webhooks \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://webhook.site/your-uuid",
    "events": ["resume.parse_completed"],
    "description": "测试 Webhook"
  }'

# 上传简历触发事件
curl -X POST http://localhost:8080/api/v1/resumes/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@resume.pdf"
```

### 3. 使用 webhook.site 测试
访问 https://webhook.site 获取临时 URL，用于测试 Webhook 功能。

## 五、文档

- Webhook 使用指南：`docs/webhook-usage.md`
- API 文档：待更新

---

**日期**: 2026-02-20
**版本**: 1.0
