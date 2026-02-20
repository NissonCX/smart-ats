# 智谱 AI 快速配置参考（2026年2月最新）

> **重要**：请在开发前仔细阅读此文档，确保使用正确的模型名称。

---

## ✅ 正确的模型名称（2026年2月）

| 模型名称 | 类型 | 说明 | 推荐场景 |
|---------|------|------|---------|
| `glm-4-plus` | 高智能模型 | 语言理解、逻辑推理 | 生产环境 |
| `glm-4-air-250414` | 基座语言模型 | 工具调用、代码智能体 | 智能体任务 |
| `glm-4-airx` | 高速版 | 快速响应 | 实时交互 |
| `glm-4-flashx-250414` | Flash 增强版 | 实时检索、长上下文 | 高并发 |
| `glm-4-flash-250414` | **免费语言模型** | 开发测试、基础任务 | **开发测试** |
| `glm-4.7` | **最新旗舰** | Agentic Coding 专用 | 高级场景 |

---

## ⚠️ 常见错误（请避免）

| ❌ 错误 | ✅ 正确 |
|---------|---------|
| `glm-4-flash` | `glm-4-flash-250414` |
| `glm-4-air` | `glm-4-air-250414` |
| `gpt-4o-mini` | `glm-4-plus` 或 `glm-4.7` |

---

## 📝 推荐配置

### 开发测试（免费）

```yaml
# application.yml
spring:
  ai:
    zhipuai:
      api-key: ${ZHIPU_API_KEY}
      chat:
        enabled: true
        options:
          model: glm-4-flash-250414  # 免费
          temperature: 0.3
          max-tokens: 4000
```

```bash
# .env
ZHIPU_API_KEY=你的密钥
ZHIPU_MODEL=glm-4-flash-250414
```

### 生产环境（推荐）

```yaml
spring:
  ai:
    zhipuai:
      api-key: ${ZHIPU_API_KEY}
      chat:
        enabled: true
        options:
          model: glm-4-plus  # 高智能
          temperature: 0.3
          max-tokens: 4000
```

```bash
# .env
ZHIPU_API_KEY=你的密钥
ZHIPU_MODEL=glm-4-plus
```

---

## 🧪 测试 API 调用

```bash
curl -X POST "https://open.bigmodel.cn/api/paas/v4/chat/completions" \
  -H "Authorization: Bearer 你的API密钥" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "glm-4-flash-250414",
    "messages": [{"role": "user", "content": "你好"}]
  }'
```

**预期结果**：
```json
{
  "choices": [
    {
      "message": {
        "content": "你好！我是智谱AI的助手..."
      }
    }
  ]
}
```

---

## 📚 官方文档

- [GLM-4 模型系列](https://docs.bigmodel.cn/cn/guide/models/text/glm-4)
- [GLM-4.7 最新旗舰](https://docs.bigmodel.cn/cn/guide/start/latest-glm-4.7)
- [智谱 AI 开放平台](https://open.bigmodel.cn/)

---

## 🔍 故障排查

### 问题：调用失败，提示模型不存在

**原因**：使用了过时的模型名称

**解决**：检查是否使用了 `-250414` 后缀

### 问题：找不到 ZhipuAiChatModel

**原因**：Spring AI 依赖未正确添加

**解决**：确认添加了 `spring-milestones` 仓库

```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

---

**最后更新**：2026-02-20
**文档版本**：v1.0
