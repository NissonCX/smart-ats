# Webhook 功能使用指南

## 概述

SmartATS 提供了完整的 Webhook 功能，允许用户订阅系统事件并接收实时通知。当特定事件发生时（如简历解析完成、新职位申请等），系统会向用户配置的回调 URL 发送 HTTP POST 请求。

## 支持的事件类型

### 简历相关事件

| 事件代码 | 事件名称 | 描述 |
|---------|---------|------|
| `resume.uploaded` | 新简历上传 | 当用户上传新简历时触发 |
| `resume.parse_completed` | 简历解析完成 | 简历成功解析并提取结构化数据时触发 |
| `resume.parse_failed` | 简历解析失败 | 简历解析失败时触发 |

### 候选人相关事件

| 事件代码 | 事件名称 | 描述 |
|---------|---------|------|
| `candidate.created` | 候选人信息创建 | 创建候选人档案时触发 |
| `candidate.updated` | 候选人信息更新 | 更新候选人信息时触发 |

### 职位申请相关事件

| 事件代码 | 事件名称 | 描述 |
|---------|---------|------|
| `application.submitted` | 提交职位申请 | 候选人提交职位申请时触发 |
| `application.status_changed` | 申请状态变更 | 职位申请状态改变时触发 |

### 面试相关事件

| 事件代码 | 事件名称 | 描述 |
|---------|---------|------|
| `interview.scheduled` | 面试安排 | 安排面试时触发 |
| `interview.completed` | 面试完成 | 面试完成时触发 |
| `interview.cancelled` | 面试取消 | 取消面试时触发 |

### 系统事件

| 事件代码 | 事件名称 | 描述 |
|---------|---------|------|
| `system.error` | 系统错误 | 系统发生错误时触发 |
| `system.maintenance` | 系统维护通知 | 系统维护通知 |

## API 使用

### 1. 创建 Webhook 配置

**请求:**
```http
POST /api/v1/webhooks
Authorization: Bearer <your_jwt_token>
Content-Type: application/json

{
  "url": "https://your-server.com/webhook",
  "events": ["resume.uploaded", "resume.parse_completed"],
  "description": "简历相关通知"
}
```

**响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "url": "https://your-server.com/webhook",
    "events": ["resume.uploaded", "resume.parse_completed"],
    "description": "简历相关通知",
    "enabled": true,
    "failureCount": 0,
    "secretHint": "a1b2****c3d4",
    "createdAt": "2026-02-20T15:30:00",
    "updatedAt": "2026-02-20T15:30:00"
  }
}
```

### 2. 查询 Webhook 配置列表

**请求:**
```http
GET /api/v1/webhooks
Authorization: Bearer <your_jwt_token>
```

**响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "url": "https://your-server.com/webhook",
      "events": ["resume.uploaded", "resume.parse_completed"],
      "enabled": true,
      "failureCount": 0,
      "lastSuccessAt": "2026-02-20T16:00:00",
      "secretHint": "a1b2****c3d4"
    }
  ]
}
```

### 3. 删除 Webhook 配置

**请求:**
```http
DELETE /api/v1/webhooks/{id}
Authorization: Bearer <your_jwt_token>
```

## Webhook 请求格式

### 请求头

```
Content-Type: application/json
User-Agent: SmartATS-Webhook/1.0
X-Webhook-Event: resume.parse_completed
X-Webhook-ID: 550e8400-e29b-41d4-a716-446655440000
X-Webhook-Signature: sha256=xxxxxxxxxxxxx
```

### 请求体示例

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "resume.parse_completed",
  "timestamp": "2026-02-20T16:00:00",
  "version": "1.0",
  "signature": "sha256=xxxxxxxxxxxxx",
  "data": {
    "taskId": "c8a15791-e250-40f6-a2da-fe25e68dbfa6",
    "resumeId": 3,
    "fileName": "张三_Java_北京大学.pdf",
    "userId": 4,
    "fileSize": 552225,
    "fileType": "application/pdf",
    "status": "COMPLETED"
  }
}
```

## 签名验证

为了验证 Webhook 请求的真实性，我们使用 HMAC-SHA256 签名。

### 验证步骤

1. 从请求头中提取 `X-Webhook-Signature`
2. 从响应体中移除 `signature` 字段
3. 将剩余的 JSON 字符串与您的密钥进行 HMAC-SHA256 运算
4. 比对计算结果与请求头中的签名

### 示例代码（Node.js）

```javascript
const crypto = require('crypto');

function verifyWebhookSignature(payload, signature, secret) {
    // 移除 signature 字段
    const { signature: _, ...data } = payload;
    const json = JSON.stringify(data);

    // 计算 HMAC-SHA256
    const hmac = crypto.createHmac('sha256', secret);
    hmac.update(json);
    const expectedSignature = 'sha256=' + hmac.digest('hex');

    // 比对签名
    return signature === expectedSignature;
}

// 使用示例
app.post('/webhook', (req, res) => {
    const signature = req.headers['x-webhook-signature'];
    const isValid = verifyWebhookSignature(req.body, signature, YOUR_WEBHOOK_SECRET);

    if (!isValid) {
        return res.status(401).send('Invalid signature');
    }

    // 处理事件
    console.log('Event:', req.body.eventType);
    console.log('Data:', req.body.data);

    res.sendStatus(200);
});
```

### 示例代码（Python）

```python
import hmac
import hashlib
import json

def verify_webhook_signature(payload, signature, secret):
    # 移除 signature 字段
    data = payload.copy()
    data.pop('signature', None)

    # 计算 HMAC-SHA256
    json_str = json.dumps(data, separators=(',', ':'))
    expected_signature = 'sha256=' + hmac.new(
        secret.encode(),
        json_str.encode(),
        hashlib.sha256
    ).hexdigest()

    # 比对签名
    return hmac.compare_digest(signature, expected_signature)

# 使用示例（Flask）
@app.route('/webhook', methods=['POST'])
def webhook():
    signature = request.headers.get('X-Webhook-Signature')
    payload = request.get_json()

    if not verify_webhook_signature(payload, signature, YOUR_WEBHOOK_SECRET):
        return 'Invalid signature', 401

    # 处理事件
    print('Event:', payload['eventType'])
    print('Data:', payload['data'])

    return 'OK', 200
```

## 重试机制

- Webhook 发送失败后会自动重试
- 连续失败 5 次后，Webhook 配置会自动禁用
- 您可以在控制台查看失败原因并重新启用

## 响应要求

您的 Webhook 端点应该：

1. **快速响应**：在 3 秒内返回响应
2. **返回 2xx 状态码**：表示成功处理
3. **返回 4xx/5xx 状态码**：表示处理失败（将触发重试）

建议返回 `200 OK` 或 `204 No Content`。

## 安全建议

1. **使用 HTTPS**：确保您的 Webhook URL 使用 HTTPS
2. **验证签名**：始终验证请求签名
3. **限制请求来源**：可以检查 IP 白名单（可选）
4. **及时处理**：Webhook 请求应该尽快处理，避免超时

## 故障排查

### Webhook 未收到通知

1. 检查 Webhook 配置是否启用
2. 查看控制台中的失败日志
3. 确认回调 URL 可以从外网访问
4. 检查签名验证是否正确

### 频繁收到重复通知

1. 检查您的响应是否返回了正确的状态码
2. 非幂等的处理可能导致重复处理，建议使用 `eventId` 去重

## 相关资源

- API 文档：`/docs/api.md`
- 技术支持：support@smartats.com
