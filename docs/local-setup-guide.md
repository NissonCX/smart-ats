# 本地开发环境配置指南

## 📋 前置条件

在开始之前，请确保已安装以下软件：

- JDK 21
- Maven 3.9+
- Docker Desktop
- IntelliJ IDEA（推荐）

## 🔧 环境变量配置

### 方法一：使用 .env 文件（推荐）

项目已创建 `.env.example` 模板文件。配置步骤：

#### 1. 创建 .env 文件

```bash
cp .env.example .env
```

#### 2. 编辑 .env 文件

填写实际的配置值：

```bash
# 邮件配置
MAIL_USERNAME=your_email@qq.com
MAIL_PASSWORD=your_mail_authorization_code

# 数据库配置
DB_PASSWORD=your_db_password

# Redis 配置
REDIS_PASSWORD=your_redis_password

# RabbitMQ 配置
RABBITMQ_PASSWORD=your_rabbitmq_password
```

#### 3. IntelliJ IDEA 配置读取 .env

**方式 A：使用 EnvFile 插件（推荐）**

1. 安装插件：
   - `File` → `Settings` → `Plugins`
   - 搜索 "EnvFile"
   - 安装 "EnvFile" 插件

2. 配置运行环境：
   - `Run` → `Edit Configurations...`
   - 选择 `Application` → `SmartATSApplication`
   - 点击 `EnvFile` 标签页
   - 勾选 `Enable EnvFile`
   - 点击 `+` 添加 `.env` 文件路径

3. 保存并运行应用

**方式 B：使用环境变量运行配置**

1. `Run` → `Edit Configurations...`
2. 选择 `Application` → `SmartATSApplication`
3. 点击 `Environment variables` 输入框旁的文件夹图标
4. 手动添加环境变量

### 方法二：系统环境变量

#### macOS / Linux

编辑 `~/.zshrc` 或 `~/.bash_profile`：

```bash
export MAIL_USERNAME=your_email@qq.com
export MAIL_PASSWORD=your_mail_password
export DB_PASSWORD=your_db_password
export REDIS_PASSWORD=your_redis_password
export RABBITMQ_PASSWORD=your_rabbitmq_password
export JWT_SECRET=SmartATS2026SecretKeyMustBeLongEnoughForHS256
export MINIO_SECRET_KEY=your_minio_secret
```

重新加载配置：

```bash
source ~/.zshrc
```

#### Windows

使用系统环境变量设置：

1. 右键 `此电脑` → `属性` → `高级系统设置`
2. 点击 `环境变量`
3. 在 `用户变量` 中添加上述环境变量

## 🐳 启动基础设施服务

使用 Docker Compose 启动所需服务：

```bash
docker-compose up -d mysql redis rabbitmq minio
```

检查服务状态：

```bash
docker-compose ps
```

## 🚀 启动应用

### 使用 Maven 启动

```bash
mvn spring-boot:run
```

### 使用 IntelliJ IDEA 启动

1. 打开 `SmartATSApplication.java`
2. 点击运行按钮或按 `Ctrl + Shift + R` (macOS: `Cmd + Shift + R`)

应用启动成功后，访问：
- 应用地址: http://localhost:8080/api/v1
- API 文档: http://localhost:8080/swagger-ui.html (如已集成)

## ✅ 验证配置

### 测试数据库连接

```bash
curl http://localhost:8080/api/v1/actuator/health
```

### 测试注册接口

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "Password123!",
    "email": "test@example.com"
  }'
```

## 🔐 密码安全建议

### 邮件授权码获取（QQ 邮箱示例）

1. 登录 QQ 邮箱网页版
2. 点击 `设置` → `账户`
3. 找到 `POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV服务`
4. 开启 `IMAP/SMTP服务`
5. 生成授权码（不是 QQ 密码）
6. 将授权码填入 `MAIL_PASSWORD`

### 生成安全的 JWT 密钥

```bash
# 使用 OpenSSL 生成 256 位随机密钥
openssl rand -base64 32
```

将生成的密钥填入 `JWT_SECRET`。

## 🐛 常见问题

### 问题 1: .env 文件不生效

**解决方案**：
- 确保 .env 文件在项目根目录
- 检查 .gitignore 是否包含 .env（不应提交）
- 在 IntelliJ IDEA 中确认 EnvFile 插件已正确配置

### 问题 2: 数据库连接失败

**解决方案**：
- 检查 Docker 容器是否运行：`docker ps`
- 确认端口配置：`DB_PORT=3307`
- 测试连接：`mysql -h localhost -P 3307 -u smartats -p`

### 问题 3: Redis 连接失败

**解决方案**：
- 检查 Redis 容器：`docker ps | grep redis`
- 确认密码配置：`REDIS_PASSWORD`
- 测试连接：`redis-cli -h localhost -p 6379 -a your_password`

## 📝 配置文件说明

| 文件 | 用途 | 是否提交到 git |
|------|------|----------------|
| `.env.example` | 环境变量模板 | ✅ 是 |
| `.env` | 本地开发实际配置 | ❌ 否 |
| `application.yml.example` | Spring 配置模板 | ✅ 是 |
| `application.yml` | Spring 配置文件 | ⚠️ 使用环境变量后可提交 |

## 🔗 相关文档

- [Spring Boot 外部化配置](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Docker Compose 入门](https://docs.docker.com/compose/gettingstarted/)
- [IntelliJ IDEA EnvFile 插件](https://plugins.jetbrains.com/plugin/7861-envfile)
