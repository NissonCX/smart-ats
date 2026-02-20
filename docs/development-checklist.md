# SmartATS 简历模块开发快速检查清单

**开发前准备清单** - 确保所有准备工作就绪后再开始编码

---

## ✅ 准备工作检查

### 1. 基础设施

- [ ] **MySQL** 运行正常（端口 3307）
  ```bash
  docker ps | grep mysql
  mysql -h localhost -P 3307 -u smartats -p
  ```

- [ ] **Redis** 运行正常（端口 6379）
  ```bash
  docker ps | grep redis
  redis-cli -h localhost -p 6379 -a redis123 ping
  ```

- [ ] **RabbitMQ** 运行正常（端口 5672）
  ```bash
  docker ps | grep rabbitmq
  # 访问管理界面 http://localhost:15672
  ```

- [ ] **MinIO** 运行正常（端口 9000）
  ```bash
  docker ps | grep minio
  # 访问 http://localhost:9000
  ```

### 2. 数据库准备

- [ ] 创建 candidates 表
  ```bash
  mysql -u smartats -p smartats < src/main/resources/db/candidates_table.sql
  ```

- [ ] 验证表创建成功
  ```sql
  USE smartats;
  SHOW TABLES;
  DESC candidates;
  ```

### 3. 环境变量配置

- [ ] 更新 `.env` 文件，添加智谱 AI 配置：
  ```bash
  ZHIPU_API_KEY=你的密钥
  ZHIPU_MODEL=glm-4-flash
  ```

- [ ] 验证密钥有效：
  ```bash
  curl -X POST https://open.bigmodel.cn/api/paas/v4/chat/completions \
    -H "Authorization: Bearer 你的密钥" \
    -H "Content-Type: application/json" \
    -d '{"model":"glm-4-flash","messages":[{"role":"user","content":"你好"}]}'
  ```

### 4. Maven 依赖

- [ ] 更新 `pom.xml`，添加依赖：
  - spring-ai-starter-model-zhipuai
  - redisson-spring-boot-starter
  - poi-ooxml
  - pdfbox

- [ ] 添加 Spring Milestone 仓库

- [ ] 执行 `mvn clean compile` 验证编译通过

### 5. 配置文件

- [ ] 更新 `application.yml`：
  ```yaml
  spring:
    ai:
      zhipuai:
        api-key: ${ZHIPU_API_KEY}
        chat:
          enabled: true
          options:
            model: ${ZHIPU_MODEL:glm-4-flash}
            temperature: 0.3
  ```

### 6. 开发工具

- [ ] IntelliJ IDEA 安装以下插件：
  - [ ] Lombok
  - [ ] EnvFile（环境变量支持）
  - [ ] MyBatisX（可选）

- [ ] 配置 EnvFile 插件读取 `.env` 文件

---

## 📝 开发顺序检查

### 阶段一：基础设施（1天）

- [ ] 1.1 添加 Maven 依赖
- [ ] 1.2 配置环境变量
- [ ] 1.3 配置 application.yml
- [ ] 1.4 创建数据库表

**完成标准**：`mvn clean compile` 成功

### 阶段二：候选人模块（2天）

- [ ] 2.1 创建 `Candidate.java` 实体
- [ ] 2.2 创建 `JsonTypeHandler.java`
- [ ] 2.3 创建 `CandidateMapper.java`
- [ ] 2.4 创建 `CandidateService.java`
- [ ] 2.5 创建 `CandidateController.java`

**完成标准**：可以通过 Postman 测试 CRUD 接口

### 阶段三：AI 解析服务（2天）

- [ ] 3.1 创建 `ZhipuAiConfig.java`
- [ ] 3.2 创建 `ResumeContentExtractor.java`
- [ ] 3.3 创建 `ResumeParseService.java`
- [ ] 3.4 创建 `CandidateInfo.java` DTO

**完成标准**：单元测试通过，能成功解析文本简历

### 阶段四：集成到消费者（1天）

- [ ] 4.1 创建 `RedissonConfig.java`
- [ ] 4.2 修改 `ResumeParseConsumer.java`
- [ ] 4.3 测试完整流程

**完成标准**：上传简历后能异步解析并保存候选人信息

### 阶段五：完善功能（2天）

- [ ] 5.1 简历列表查询
- [ ] 5.2 简历详情查询
- [ ] 5.3 简历删除功能
- [ ] 5.4 单元测试和集成测试

**完成标准**：所有功能测试通过

---

## 🔍 开发中检查点

### 每个阶段完成后

- [ ] 代码编译通过
- [ ] 单元测试通过
- [ ] Git commit（中文提交信息）

### 提交前检查

- [ ] 代码符合项目规范
- [ ] 敏感信息不在代码中
- [ ] 日志级别正确（DEBUG/INFO/WARN/ERROR）
- [ ] 异常处理完善
- [ ] 注释清晰

---

## 🚨 常见问题速查

### 编译问题

**Q: 找不到 ZhipuAiChatModel**
```bash
A: 检查是否添加了 Spring Milestone 仓库
```

**Q: 依赖下载失败**
```bash
A: 配置 Maven 镜像（阿里云）
```

### 运行时问题

**Q: 智谱 API 调用失败**
```bash
A: 检查 API Key 是否正确，账户是否有余额
```

**Q: Redisson 连接失败**
```bash
A: 确认 Redis 已启动，密码配置正确
```

**Q: 文件提取失败**
```bash
A: 检查 MinIO 文件 URL 是否可访问
```

---

## 📚 参考文档

| 文档 | 用途 |
|------|------|
| `resume-module-enhancement-guide.md` | 主要开发手册 |
| `zhipu-ai-integration-guide.md` | 智谱 AI 详细指南 |
| `spring-ai-vs-spring-ai-alibaba-analysis.md` | 技术选型分析 |
| `candidates_table.sql` | 数据库表结构 |

---

## ✅ 准备开始

当以上所有检查项都完成后，您就可以开始开发了！

**建议**：
1. 按照阶段顺序逐步实现
2. 每完成一个阶段就测试验证
3. 遇到问题先查看日志
4. 参考文档中的代码示例

**祝开发顺利！** 🚀
