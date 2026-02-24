# SmartATS 生产部署指南

## 目录

- [环境要求](#环境要求)
- [快速部署（Docker Compose）](#快速部署docker-compose)
- [分步部署](#分步部署)
- [环境变量配置](#环境变量配置)
- [数据库初始化](#数据库初始化)
- [健康检查](#健康检查)
- [常见问题](#常见问题)
- [监控与日志](#监控与日志)
- [备份策略](#备份策略)

---

## 环境要求

| 组件 | 最低版本 | 推荐版本 |
|------|----------|----------|
| Docker | 24.0+ | 最新稳定版 |
| Docker Compose | 2.20+ | 最新稳定版 |
| JDK（源码构建） | 21 | 21 LTS |
| 内存 | 4 GB | 8 GB+ |
| 磁盘 | 20 GB | 50 GB+ (含 Milvus 向量数据) |

**端口清单（确保未被占用）：**

| 服务 | 端口 | 用途 |
|------|------|------|
| SmartATS App | 8080 | API 服务 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 & 分布式锁 |
| RabbitMQ AMQP | 5672 | 消息队列 |
| RabbitMQ Management | 15672 | MQ 管理界面 |
| MinIO API | 9000 | 文件存储 |
| MinIO Console | 9001 | 文件管理界面 |
| Milvus | 19530 | 向量数据库 |
| Milvus Metrics | 9091 | Milvus 健康检查 |
| etcd | 2379 | Milvus 元数据 |

---

## 快速部署（Docker Compose）

### 1. 克隆项目

```bash
git clone https://github.com/NissonCX/SmartATS.git
cd SmartATS
```

### 2. 配置环境变量

```bash
cp .env.example .env
vim .env
```

编辑 `.env` 文件，设置所有必需的环境变量（详见[环境变量配置](#环境变量配置)）。

### 3. 构建 JAR 包

```bash
# 需要 JDK 21 + Maven 3.9+
mvn clean package -DskipTests
```

### 4. 启动所有基础设施服务

```bash
docker-compose up -d
```

### 5. 等待服务就绪

```bash
# 检查所有服务状态
docker-compose ps

# 等待 MySQL 就绪
docker-compose exec mysql mysqladmin ping -h localhost -uroot -proot123

# 等待 Milvus 就绪（首次启动需要 90 秒）
curl -sf http://localhost:9091/healthz
```

### 6. 初始化数据库

```bash
# 执行建表脚本
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < docker/mysql/init/01-init-database.sql
mysql -h 127.0.0.1 -P 3307 -u smartats -psmartats123 smartats < src/main/resources/db/webhook_tables.sql
```

### 7. 启动应用

```bash
# 使用生产配置启动
java -jar target/smartats-1.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

### 8. 验证部署

```bash
# 健康检查
curl http://localhost:8080/api/v1/auth/test

# 预期返回：未认证 403（正常，说明服务已启动）
```

---

## 分步部署

如果需要将各组件部署在不同服务器上，按以下顺序操作：

### Step 1: MySQL

```bash
docker run -d \
  --name smartats-mysql \
  -e MYSQL_ROOT_PASSWORD=<strong_root_password> \
  -e MYSQL_DATABASE=smartats \
  -e MYSQL_USER=smartats \
  -e MYSQL_PASSWORD=<strong_db_password> \
  -e TZ=Asia/Shanghai \
  -p 3306:3306 \
  -v /data/mysql:/var/lib/mysql \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

### Step 2: Redis

```bash
docker run -d \
  --name smartats-redis \
  -p 6379:6379 \
  -v /data/redis:/data \
  redis:7.0-alpine \
  redis-server --appendonly yes --requirepass <strong_redis_password>
```

### Step 3: RabbitMQ

```bash
docker run -d \
  --name smartats-rabbitmq \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=<strong_mq_password> \
  -e RABBITMQ_DEFAULT_VHOST=smartats \
  -p 5672:5672 \
  -p 15672:15672 \
  -v /data/rabbitmq:/var/lib/rabbitmq \
  rabbitmq:3.12-management
```

### Step 4: MinIO

```bash
docker run -d \
  --name smartats-minio \
  -e MINIO_ROOT_USER=admin \
  -e MINIO_ROOT_PASSWORD=<strong_minio_password> \
  -p 9000:9000 \
  -p 9001:9001 \
  -v /data/minio:/data \
  minio/minio:latest \
  server /data --console-address ":9001"
```

### Step 5: Milvus (含 etcd)

```bash
# etcd
docker run -d \
  --name smartats-etcd \
  -e ETCD_AUTO_COMPACTION_MODE=revision \
  -e ETCD_AUTO_COMPACTION_RETENTION=1000 \
  -e ETCD_QUOTA_BACKEND_BYTES=4294967296 \
  -v /data/etcd:/etcd \
  quay.io/coreos/etcd:v3.5.16 \
  etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd

# Milvus
docker run -d \
  --name smartats-milvus \
  -e ETCD_ENDPOINTS=<etcd_host>:2379 \
  -e MINIO_ADDRESS=<minio_host>:9000 \
  -e MINIO_ACCESS_KEY_ID=admin \
  -e MINIO_SECRET_ACCESS_KEY=<strong_minio_password> \
  -p 19530:19530 \
  -p 9091:9091 \
  -v /data/milvus:/var/lib/milvus \
  milvusdb/milvus:v2.4.17 \
  milvus run standalone
```

### Step 6: SmartATS 应用

```bash
java -jar smartats-1.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

---

## 环境变量配置

### `.env` 文件模板

```env
# ==================== 数据库 ====================
DB_HOST=localhost
DB_PORT=3306
DB_NAME=smartats
DB_USERNAME=smartats
DB_PASSWORD=<strong_db_password>

# ==================== Redis ====================
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<strong_redis_password>

# ==================== RabbitMQ ====================
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=<strong_mq_password>
RABBITMQ_VHOST=smartats

# ==================== MinIO ====================
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=admin
MINIO_SECRET_KEY=<strong_minio_password>

# ==================== Milvus ====================
MILVUS_HOST=localhost
MILVUS_PORT=19530
MILVUS_DATABASE=default

# ==================== AI（智谱） ====================
ZHIPU_API_KEY=<your_zhipu_api_key>
ZHIPU_MODEL=glm-4-flash-250414
AI_EMBEDDING_MODEL=embedding-3
AI_DAILY_QUOTA=500

# ==================== JWT ====================
JWT_SECRET=<random_string_at_least_32_chars>

# ==================== 邮件 ====================
MAIL_HOST=smtp.qq.com
MAIL_PORT=587
MAIL_USERNAME=<your_email@qq.com>
MAIL_PASSWORD=<your_smtp_auth_code>

# ==================== CORS ====================
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.com

# ==================== 服务器 ====================
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

### 关键配置说明

| 变量 | 必填 | 说明 |
|------|:----:|------|
| `DB_PASSWORD` | ✅ | MySQL 密码，禁止使用默认值 |
| `REDIS_PASSWORD` | ✅ | Redis 密码 |
| `ZHIPU_API_KEY` | ✅ | 智谱 AI API Key（[控制台获取](https://open.bigmodel.cn)） |
| `JWT_SECRET` | ✅ | JWT 签名密钥，≥32 字符随机字符串 |
| `MAIL_PASSWORD` | ✅ | SMTP 授权码（非邮箱登录密码） |
| `CORS_ALLOWED_ORIGINS` | ✅ | 前端域名，多个用逗号分隔 |
| `AI_DAILY_QUOTA` | ❌ | 每日 AI 调用上限，默认 500 |

> ⚠️ **安全提醒**：`.env` 文件已在 `.gitignore` 中排除，切勿提交到版本库。

---

## 数据库初始化

### 首次部署

数据库建表脚本在 `docker/mysql/init/01-init-database.sql`，Docker Compose 会在首次启动时自动执行。

如需手动初始化：

```bash
mysql -h <DB_HOST> -P <DB_PORT> -u smartats -p<DB_PASSWORD> smartats < docker/mysql/init/01-init-database.sql
mysql -h <DB_HOST> -P <DB_PORT> -u smartats -p<DB_PASSWORD> smartats < src/main/resources/db/webhook_tables.sql
```

### 数据库表概览

| 表名 | 说明 |
|------|------|
| `users` | 用户账号（ADMIN/HR/INTERVIEWER） |
| `jobs` | 职位信息 |
| `resumes` | 简历文件（MD5 去重） |
| `candidates` | AI 提取的候选人结构化数据 |
| `job_applications` | 职位申请记录 |
| `interview_records` | 面试记录 |
| `webhook_configs` | Webhook 配置 |
| `webhook_logs` | Webhook 投递日志 |

---

## 健康检查

### 应用层

```bash
# 基础连通性（需要认证的接口返回 403 即说明服务正常）
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/jobs
# 预期：200

# Swagger 文档（仅 dev 环境可用，prod 已禁用）
curl http://localhost:8080/api/v1/swagger-ui.html
```

### 基础设施

```bash
# MySQL
docker exec smartats-mysql mysqladmin ping -h localhost -uroot -p<password>

# Redis
docker exec smartats-redis redis-cli -a <password> ping

# RabbitMQ
curl -u admin:<password> http://localhost:15672/api/healthchecks/node

# MinIO
curl -sf http://localhost:9000/minio/health/live

# Milvus
curl -sf http://localhost:9091/healthz
```

---

## 常见问题

### 1. Milvus 启动慢

Milvus 首次启动需要约 90 秒，`docker-compose.yml` 中已配置 `start_period: 90s`。如果依赖服务未就绪，Milvus 可能反复重启。确保 etcd 和 MinIO 先启动。

### 2. RabbitMQ VHost 不存在

如果出现 `NOT_ALLOWED - vhost smartats not found` 错误：

```bash
# 手动创建 VHost
docker exec smartats-rabbitmq rabbitmqctl add_vhost smartats
docker exec smartats-rabbitmq rabbitmqctl set_permissions -p smartats admin ".*" ".*" ".*"
```

### 3. MinIO Bucket 不存在

应用启动时会自动创建 `smartats-resumes` bucket。如需手动创建：

```bash
docker exec smartats-minio mc alias set local http://localhost:9000 admin <password>
docker exec smartats-minio mc mb local/smartats-resumes
```

### 4. AI 解析失败

检查：
- `ZHIPU_API_KEY` 是否有效
- 网络是否能访问 `https://open.bigmodel.cn`
- AI 配额是否用完（查看 Redis `rate:ai:{userId}:{date}`）

### 5. 邮件发送失败

- QQ 邮箱需要开启 SMTP 服务并获取授权码
- `MAIL_PASSWORD` 是授权码，不是邮箱登录密码
- 确保服务器出站 587 端口未被防火墙拦截

---

## 监控与日志

### 日志配置

生产环境日志：
- 输出路径：`logs/smartats.log`
- 滚动策略：单文件最大 100MB，保留 30 天
- 日志级别：`root=WARN`，`com.smartats=INFO`

```bash
# 实时查看日志
tail -f logs/smartats.log

# 搜索错误
grep ERROR logs/smartats.log
```

### RabbitMQ 监控

- 管理界面：`http://<host>:15672`
- 用户名/密码：以环境变量配置为准
- 关注：`resume.parse.queue` 消息积压、`resume.parse.dlq` 死信队列

### Redis 监控

```bash
# 查看内存使用
redis-cli -a <password> INFO memory

# 查看 key 数量
redis-cli -a <password> DBSIZE

# 查看慢查询
redis-cli -a <password> SLOWLOG GET 10
```

---

## 备份策略

### MySQL 备份

```bash
# 每日全量备份（建议 cron 定时任务）
mysqldump -h <DB_HOST> -P <DB_PORT> -u smartats -p<DB_PASSWORD> \
  --single-transaction --routines --triggers smartats \
  | gzip > /backup/smartats_$(date +%Y%m%d_%H%M%S).sql.gz

# 保留最近 7 天
find /backup -name "smartats_*.sql.gz" -mtime +7 -delete
```

### MinIO 备份

```bash
# 使用 mc mirror 同步到备份存储
mc alias set local http://localhost:9000 admin <password>
mc alias set backup <backup_endpoint> <access_key> <secret_key>
mc mirror local/smartats-resumes backup/smartats-resumes
```

### Redis 备份

Redis 已开启 AOF 持久化（`appendonly yes`），数据存储在 `./data/redis/` 目录。定期备份该目录即可。

---

## Nginx 反向代理配置（可选）

```nginx
upstream smartats {
    server 127.0.0.1:8080;
}

server {
    listen 443 ssl;
    server_name api.your-domain.com;

    ssl_certificate     /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;

    client_max_body_size 50M;

    location /api/v1/ {
        proxy_pass http://smartats;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket（如果需要）
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}

server {
    listen 80;
    server_name api.your-domain.com;
    return 301 https://$server_name$request_uri;
}
```

---

## 性能调优建议

| 配置项 | 开发环境 | 生产环境建议 |
|--------|----------|-------------|
| JVM 堆内存 | 默认 | `-Xms512m -Xmx2g` |
| HikariCP 连接池 | 20 | 20-50（按并发调整） |
| Redis 连接池 | 20 | 20-50 |
| RabbitMQ prefetch | 1 | 3-10 |
| AI 每日配额 | 100 | 500+ |
| 文件上传大小 | 10MB | 10MB |

```bash
# 生产环境 JVM 启动参数示例
java -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Dspring.profiles.active=prod \
  -jar smartats-1.0.0.jar
```
