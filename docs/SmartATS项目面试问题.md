# SmartATS 项目面试题库

> 面试官视角：针对 SmartATS 招聘管理系统的深度技术面试题

---

## 一、Spring框架与核心原理（八股 + 实战）

### 1.1 Spring Boot自动装配
**理论题**：Spring Boot的自动装配原理是什么？@SpringBootApplication注解做了什么？

**实战题**：项目中使用了多个@Configuration类（如SecurityConfig、RabbitMQConfig、RedissonConfig），请解释它们的加载顺序和依赖关系如何处理？

---

### 1.2 事务管理
**理论题**：@Transactional注解的失效场景有哪些？为什么项目中统一使用`@Transactional(rollbackFor = Exception.class)`？

**实战题**：ResumeParseConsumer中的简历解析流程涉及文件提取、AI解析、候选人保存、向量化等多个操作，为什么没有加事务？如果中间某个步骤失败，数据一致性如何保证？

---

### 1.3 Spring AOP
**理论题**：Spring AOP的代理方式有哪些？JDK动态代理和CGLIB的区别是什么？

**实战题**：项目中的@Async注解为何必须抽到独立的CacheEvictionService中才能生效？如果直接在JobService内部调用异步方法会发生什么？

---

### 1.4 Spring Security
**理论题**：Spring Security的认证和授权流程是怎样的？JWT在Spring Security中如何集成？

**实战题**：项目采用JWT无状态认证，访问令牌2小时，刷新令牌7天。请分析：
1. JWT的存储位置是什么？
2. 刷新令牌的过期时间为什么设计为7天？
3. 如果用户连续7天未访问，下次登录会发生什么？

---

## 二、Redis深度应用（核心考点）

### 2.1 缓存策略
**理论题**：Cache-Aside、Read-Through、Write-Through、Write-Behind四种缓存模式的区别是什么？

**实战题**：项目采用Cache-Aside模式，请解释JobService.getJobDetail()中的缓存逻辑：

```java
// 原子自增 Redis 计数器
Long redisIncrement = redisTemplate.opsForValue().increment(counterKey);

// 尝试读取缓存
String cachedJson = redisTemplate.opsForValue().get(cacheKey);
if (cachedJson != null) {
    JobResponse cached = objectMapper.readValue(cachedJson, JobResponse.class);
    cached.setViewCount(baseViewCount + redisIncrement.intValue());
    return cached;
}

// 缓存未命中，查库并回填
Job job = jobMapper.selectById(id);
response.setViewCount(baseViewCount + redisIncrement.intValue());
redisTemplate.opsForValue().set(cacheKey, json, 30, TimeUnit.MINUTES);
```

**追问**：为什么使用原子计数器而不是直接更新数据库的view_count字段？如果服务重启，Redis中的计数器会丢失吗？

---

### 2.2 延迟双删
**理论题**：什么是延迟双删？为什么需要延迟？延迟时间如何确定？

**实战题**：CacheEvictionService中的延迟双删实现如下：

```java
@Async("asyncExecutor")
public void asyncDeleteCache(String cacheKey) {
    Thread.sleep(500);
    redisTemplate.delete(cacheKey);
}
```

**追问**：
1. 为什么延迟时间是500ms而不是1秒？
2. 第一次删除缓存是在JobService.updateJob()的开头，第二次删除在更新DB后异步执行。这个顺序能否交换？为什么？
3. 如果异步延迟删除失败（如线程池拒绝），会有什么影响？

---

### 2.3 分布式锁
**理论题**：Redisson分布式锁的实现原理是什么？Watchdog机制是如何工作的？

**实战题**：ResumeParseConsumer中针对fileHash加分布式锁：

```java
RLock lock = redissonClient.getLock(lockKey);
boolean acquired = lock.tryLock(10, 300, TimeUnit.SECONDS);
if (!acquired) {
    channel.basicAck(deliveryTag, false);
    return;
}
```

**追问**：
1. lockKey的设计是否合理？如果两个不同的文件MD5碰撞会怎样？
2. 为什么等待时间设置为10秒，锁自动释放时间设置为300秒？
3. 如果加锁成功，但解析过程中服务宕机，锁会自动释放吗？

---

### 2.4 幂等性设计
**理论题**：什么是幂等性？在分布式系统中如何保证幂等性？

**实战题**：ResumeParseConsumer中使用Redis setIfAbsent保证幂等：

```java
String idempotentKey = "idempotent:resume:" + resumeId;
Boolean alreadyProcessed = redisTemplate.opsForValue()
        .setIfAbsent(idempotentKey, "1", 1, TimeUnit.HOURS);
if (Boolean.FALSE.equals(alreadyProcessed)) {
    channel.basicAck(deliveryTag, false);
    return;
}
```

**追问**：
1. 幂等检查的TTL设置为1小时，如果业务要求48小时内不重复解析，应该如何改进？
2. 幂等键使用resumeId而不是taskId，为什么？
3. 如果消息被重复消费（如RabbitMQ网络抖动导致ACK丢失），这个幂等检查能防止重复解析吗？

---

### 2.5 原子计数器
**理论题**：Redis INCR命令的实现原理是什么？在高并发场景下如何保证计数准确性？

**实战题**：项目使用Redis INCR + 定期同步到DB的方案统计浏览量：

```java
// 每次访问都自增
Long redisIncrement = redisTemplate.opsForValue().increment(counterKey);

// 总浏览量 = DB累积值 + Redis增量
response.setViewCount(baseViewCount + redisIncrement.intValue());
```

**追问**：
1. 这种方案与直接更新DB相比，优缺点是什么？
2. 如果需要定时同步Redis计数器到DB，应该使用什么策略？GETDEL还是先GET后DEL？
3. 如何防止Redis计数器丢失导致的数据不一致？

---

## 三、消息队列与异步处理

### 3.1 RabbitMQ核心概念
**理论题**：RabbitMQ的Exchange、Queue、Binding、Routing Key分别是什么？Direct、Fanout、Topic三种Exchange的区别？

**实战题**：项目的RabbitMQ配置如下：

```java
@Bean
public DirectExchange resumeExchange() {
    return new DirectExchange(RabbitMQConfig.RESUME_EXCHANGE, true, false);
}

@Bean
public Queue resumeParseQueue() {
    return QueueBuilder.durable(RabbitMQConfig.RESUME_PARSE_QUEUE)
            .withArgument("x-dead-letter-exchange", RabbitMQConfig.DLX)
            .withArgument("x-dead-letter-routing-key", RabbitMQConfig.DLQ_ROUTING_KEY)
            .build();
}
```

**追问**：
1. 为什么选择Direct Exchange而不是Topic Exchange？
2. 死信队列（DLX）的配置是必要的吗？为什么？
3. durable参数的作用是什么？如果设置为false会有什么后果？

---

### 3.2 消息可靠性
**理论题**：如何保证RabbitMQ消息不丢失？生产端、Broker端、消费端分别需要做什么？

**实战题**：ResumeParseConsumer中手动ACK的处理逻辑：

```java
try {
    // 业务处理
    channel.basicAck(deliveryTag, false);
} catch (Exception e) {
    retryOrReject(channel, deliveryTag, message);
}

private void retryOrReject(Channel channel, long deliveryTag, ResumeParseMessage message) throws IOException {
    if (retryCount < 3) {
        message.setRetryCount(retryCount + 1);
        channel.basicPublish(exchange, routingKey, properties, json.getBytes());
        channel.basicAck(deliveryTag, false);  // ACK原消息
    } else {
        channel.basicNack(deliveryTag, false, false);  // 拒绝，不重排队
    }
}
```

**追问**：
1. 为什么不使用`basicNack(deliveryTag, false, true)`来触发RabbitMQ的重试机制，而是自己重发消息？
2. retryCount存储在消息体中还是消息头中？为什么？
3. 如果消费端处理成功，但在发送ACK前网络断开，消息会被重复消费吗？幂等性检查能覆盖这种场景吗？

---

### 3.3 消息顺序与并发
**理论题**：如何保证消息的顺序性？单线程消费 vs 多线程消费的区别？

**实战题**：假设同一个fileHash对应的两条简历解析消息先后进入队列，消费者采用多线程处理，如何保证先发的先处理？

---

### 3.4 消息堆积处理
**理论题**：如何监控和处理RabbitMQ消息堆积？消费者扩容的注意事项？

**实战题**：如果简历解析消息堆积了10万条，排查思路是什么？如何快速处理？

---

## 四、分布式系统与高并发

### 4.1 分布式事务
**理论题**：CAP理论是什么？BASE理论是什么？2PC、3PC、TCC、Saga、本地消息表的区别？

**实战题**：简历上传流程中涉及MinIO存储、MySQL写入、MQ发送、Redis缓存，如何保证数据一致性？

---

### 4.2 接口限流
**理论题**：常见的限流算法有哪些？令牌桶、漏桶、固定窗口、滑动窗口的区别？

**实战题**：项目中使用Redis实现批量上传限流：

```java
String rateLimitKey = "rate:upload:" + userId;
String countStr = stringRedisTemplate.opsForValue().get(rateLimitKey);
if (countStr != null && Integer.parseInt(countStr) >= 5) {
    throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "批量上传频率过高");
}
stringRedisTemplate.opsForValue().increment(rateLimitKey);
stringRedisTemplate.expire(rateLimitKey, 60, TimeUnit.SECONDS);
```

**追问**：
1. 这种实现是固定窗口还是滑动窗口？
2. 如果用户在第59秒上传了5个文件，然后在第61秒又上传了5个文件，实际QPS是多少？
3. 如何改造成滑动窗口限流？

---

### 4.3 高并发缓存击穿
**理论题**：缓存穿透、缓存击穿、缓存雪崩的区别是什么？各自的解决方案？

**实战题**：假设某个热门职点的缓存过期，此时有1000个并发请求访问该接口，如何防止缓存击穿？

---

## 五、数据库与ORM

### 5.1 索引原理
**理论题**：MySQL索引的数据结构是什么？B+树的特点是什么？聚簇索引和非聚簇索引的区别？

**实战题**：resumes表的file_hash字段加了唯一索引，为什么？

```java
public Long getResumeIdByFileHash(String fileHash) {
    LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<Resume>()
            .eq(Resume::getFileHash, fileHash)
            .select(Resume::getId);  // 覆盖索引
    Resume resume = resumeMapper.selectOne(wrapper);
    return resume != null ? resume.getId() : null;
}
```

**追问**：
1. 为什么要加select(Resume::getId)？
2. 如果file_hash索引是普通索引而不是唯一索引，查询性能会有什么影响？

---

### 5.2 SQL优化与N+1问题
**理论题**：什么是N+1问题？如何避免？

**实战题**：SmartSearchService中批量查询候选人信息：

```java
List<Long> candidateIds = filteredResults.stream()
        .map(SearchResult::getCandidateId)
        .toList();
List<Candidate> candidates = candidateMapper.selectBatchIds(candidateIds);
Map<Long, Candidate> candidateMap = candidates.stream()
        .collect(Collectors.toMap(Candidate::getId, Function.identity()));
```

**追问**：
1. 为什么不使用for循环逐个查询？
2. selectBatchIds的SQL是什么？如果candidateIds有1000个，SQL长度会超限吗？

---

### 5.3 事务隔离级别
**理论题**：MySQL的四种事务隔离级别是什么？默认是哪种？解决了哪些并发问题？

**实战题**：简历创建和候选人保存在不同的事务中，如果简历创建成功但候选人保存失败，数据如何回滚？

---

### 5.4 MyBatis-Plus
**理论题**：MyBatis-Plus的LambdaQueryWrapper相对于字符串QueryWrapper的优势是什么？

**实战题**：项目要求统一使用LambdaQueryWrapper，请解释：

```java
// ✅ 推荐
LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<Job>()
        .eq(Job::getStatus, JobStatus.PUBLISHED.getCode())
        .like(Job::getTitle, keyword);

// ❌ 禁止
QueryWrapper<Job> wrapper = new QueryWrapper<Job>()
        .eq("status", "PUBLISHED")
        .like("title", keyword);
```

**追问**：
1. 如果字段名重构，哪种方式更安全？
2. LambdaQueryWrapper的性能损耗在哪里？

---

## 六、微服务与容器化

### 6.1 Docker与容器编排
**理论题**：Docker和虚拟机的区别是什么？Kubernetes的核心概念有哪些？

**实战题**：项目的docker-compose.yml配置了MySQL、Redis、RabbitMQ、MinIO、Milvus，如果需要部署到生产环境，会面临哪些问题？

---

### 6.2 服务注册与发现
**理论题**：服务注册中心的作用是什么？Eureka、Nacos、Consul的区别？

**实战题**：项目目前是单体应用，如果拆分为简历服务、职位服务、候选人服务，如何解决服务间调用问题？

---

## 七、AI与向量数据库（特色亮点）

### 7.1 向量数据库原理
**理论题**：什么是向量相似度搜索？ANN（近似最近邻）算法有哪些？IVF_FLAT、HNSW、PQ的区别？

**实战题**：项目中使用IVF_FLAT索引，参数配置如下：

```java
private static final int NLIST = 128;  // IVF_FLAT 聚类数
```

**追问**：
1. NLIST参数的作用是什么？如何调优？
2. 为什么选择COSINE距离而不是欧氏距离？
3. 如果数据量达到1000万条，IVF_FLAT是否还能满足性能要求？如何优化？

---

### 7.2 Embedding模型
**理论题**：什么是Word2Vec、BERT、GPT？Embedding向量的维度如何选择？

**实战题**：项目使用智谱AI的embedding-3模型生成1024维向量：

```java
public List<Float> generateCandidateEmbedding(Candidate candidate) {
    String text = buildCandidateText(candidate);
    return embed(text);
}

private String buildCandidateText(Candidate candidate) {
    // 拼接姓名、学历、技能、工作经历等
}
```

**追问**：
1. 为什么要拼接这些字段？权重如何分配？
2. 文本截断到6000字符的原因是什么？
3. 如果候选人的自我评价很长（如2000字），会淹没其他信息吗？

---

### 7.3 RAG（检索增强生成）
**理论题**：什么是RAG？RAG vs Fine-tuning的区别是什么？

**实战题**：SmartSearchService的RAG流程如下：

```
查询文本 → Embedding → Milvus搜索 → MySQL补充详情 → 组装响应
```

**追问**：
1. 为什么需要MySQL补充详情？为什么不把所有信息都存入Milvus？
2. 如果Milvus返回的TopK候选人在MySQL中已被删除，如何处理？
3. 相似度阈值设置为0.3的依据是什么？

---

## 八、系统设计与架构

### 8.1 系统设计
**场景题**：假设需要支持10万+简历的批量解析和秒级检索，如何设计系统架构？

**追问**：
1. 如何保证解析顺序？
2. 如何处理AI服务的限流和降级？
3. 如何保证向量搜索的QPS达到1000+？

---

### 8.2 异地多活
**理论题**：什么是异地多活？数据一致性如何保证？

**实战题**：如果SmartATS需要在上海和北京两地部署，如何设计？

---

### 8.3 监控与告警
**理论题**：APM监控的核心指标有哪些？Prometheus + Grafana的架构是什么？

**实战题**：项目目前的日志输出较多，如何设计监控告警体系？

---

## 九、安全与风控

### 9.1 认证与授权
**理论题**：OAuth2.0的四种授权模式是什么？PKCE的作用是什么？

**实战题**：项目采用JWT认证，如果需要集成第三方登录（如微信扫码），如何设计？

---

### 9.2 数据脱敏
**理论题**：常见的脱敏算法有哪些？动态脱敏 vs 静态脱敏的区别？

**实战题**：项目中DataMaskUtil的脱敏逻辑如下：

```java
public static String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) {
        return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
}
```

**追问**：
1. 为什么手机号保留前3位和后4位？
2. 脱敏操作应该在数据库层还是应用层？
3. 如果需要导出原始数据给管理员，如何实现？

---

### 9.3 文件上传安全
**理论题**：文件上传的安全风险有哪些？如何防范？

**实战题**：FileValidationUtil的校验逻辑如下：

```java
public static void validateFile(String filename, String contentType, byte[] fileBytes) {
    // 1. 文件后缀名校验
    if (!ALLOWED_EXTENSIONS.contains(getFileExtension(filename))) {
        throw new BusinessException(ResultCode.INVALID_FILE_TYPE, "不支持的文件类型");
    }

    // 2. Content-Type校验
    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
        throw new BusinessException(ResultCode.INVALID_FILE_TYPE, "Content-Type不匹配");
    }

    // 3. Magic Number校验
    if (!validateMagicNumber(fileBytes)) {
        throw new BusinessException(ResultCode.INVALID_FILE_TYPE, "文件内容与后缀名不匹配");
    }
}
```

**追问**：
1. Magic Number是什么？为什么需要校验？
2. 如果用户上传了一个伪装成PDF的可执行文件，这个校验能拦截吗？
3. 文件大小限制（10MB）如何避免DoS攻击？

---

## 十、性能优化与调优

### 10.1 JVM调优
**理论题**：JVM内存模型是怎样的？GC算法有哪些？G1和CMS的区别？

**实战题**：简历解析服务需要处理大文件，JVM参数应该如何设置？

---

### 10.2 数据库优化
**理论题**：慢SQL的优化思路是什么？Explain的执行计划如何解读？

**实战题**：候选人列表查询包含多条件筛选和分页，如何优化SQL？

```java
LambdaQueryWrapper<Candidate> queryWrapper = new LambdaQueryWrapper<>();
if (education != null) {
    queryWrapper.eq(Candidate::getEducation, education);
}
if (minWorkYears != null) {
    queryWrapper.ge(Candidate::getWorkYears, minWorkYears);
}
// ... 更多条件
```

**追问**：
1. 如何设计联合索引？
2. 如果索引失效，可能的原因是什么？

---

### 10.3 缓存性能
**理论题**：Redis的BigKey、HotKey问题如何解决？

**实战题**：如果热门职点的缓存QPS达到10万+，如何优化？

---

## 十一、代码质量与工程实践

### 11.1 设计模式
**理论题**：常见的设计模式有哪些？在项目中如何应用？

**实战题**：项目中使用了哪些设计模式？举例说明。

---

### 11.2 异常处理
**理论题**：Checked Exception vs Unchecked Exception的区别是什么？何时使用哪种？

**实战题**：GlobalExceptionHandler中的异常优先级设计：

```java
@ExceptionHandler(BusinessException.class)
public Result<?> handleBusinessException(BusinessException e) {
    log.warn("业务异常: {}", e.getMessage());
    return Result.error(e.getCode(), e.getMessage());
}

@ExceptionHandler(Exception.class)
public Result<?> handleException(Exception e) {
    log.error("系统异常", e);
    return Result.error(ResultCode.INTERNAL_ERROR);
}
```

**追问**：
1. 为什么BusinessException放在最前面？
2. 如果BusinessException被放在Exception之后，会怎么样？

---

### 11.3 测试策略
**理论题**：单元测试、集成测试、端到端测试的区别是什么？测试覆盖率的标准是什么？

**实战题**：项目中的SmartSearchServiceTest使用了Mock隔离：

```java
@ExtendWith(MockitoExtension.class)
class SmartSearchServiceTest {
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStoreService vectorStoreService;
    @Mock private CandidateMapper candidateMapper;
    // ...
}
```

**追问**：
1. 为什么不启动真实的Milvus进行集成测试？
2. 如果需要测试完整的解析流程（文件上传→AI解析→候选人保存），应该怎么设计测试？

---

## 十二、场景题与开放性问题

### 12.1 简历解析优化
**场景题**：用户反馈简历解析准确率只有85%，如何提升？

**追问**：
1. AI模型的Prompt如何优化？
2. 是否需要接入多个AI模型进行交叉验证？
3. 如何收集用户的反馈数据？

---

### 12.2 扩展性设计
**场景题**：如果需要支持多语言简历（英文、日文、韩文），系统需要做哪些改造？

---

### 12.3 高可用设计
**场景题**：如果智谱AI服务宕机，简历解析流程如何降级？

---

### 12.4 数据迁移
**场景题**：如果需要将简历文件从MinIO迁移到阿里云OSS，如何设计迁移方案？

---

### 12.5 性能瓶颈
**场景题**：压测发现向量搜索的响应时间平均500ms，目标是50ms，如何优化？

---

## 面试官提问建议

### 针对不同级别候选人的侧重点

| 级别 | 侧重点 | 推荐问题 |
|------|--------|----------|
| 初级（1-3年） | 基础知识 | 1.1, 5.1, 5.4, 9.2, 11.2 |
| 中级（3-5年） | 项目实现 | 2.1, 2.2, 2.3, 3.2, 7.1 |
| 高级（5-8年） | 系统设计 | 4.1, 4.2, 8.1, 10.2, 12.1 |
| 资深（8年+） | 架构能力 | 8.2, 8.3, 10.3, 12.3, 12.5 |

### 提问技巧

1. **循序渐进**：从基础概念切入，逐步深入到项目实践
2. **追问细节**：不要满足于表面答案，追问"为什么"、"如何改进"
3. **对比分析**：要求候选人对比不同方案的优劣
4. **场景假设**：设置具体业务场景，考察解决问题的思路
5. **代码审查**：让候选人阅读项目代码，指出潜在问题

### 评分维度

| 维度 | 权重 | 评分标准 |
|------|------|----------|
| 基础知识 | 20% | 八股知识掌握程度，原理理解深度 |
| 项目实践 | 30% | 对自身项目的理解程度，能否解释技术选型 |
| 问题解决 | 25% | 面对未知问题的分析思路，解决方案的合理性 |
| 代码质量 | 15% | 代码规范意识，设计模式应用 |
| 沟通表达 | 10% | 逻辑清晰度，能否简洁表达复杂概念 |

---

## 附录：关键代码片段索引

| 功能 | 文件路径 | 关键代码行 |
|------|----------|------------|
| 延迟双删 | JobService.java:88, CacheEvictionService.java:33 | 2.2 |
| 分布式锁 | ResumeParseConsumer.java:78-88 | 2.3 |
| 幂等性检查 | ResumeParseConsumer.java:67-75 | 2.4 |
| 原子计数器 | JobService.java:187, 229 | 2.5 |
| MQ重试 | ResumeParseConsumer.java:237-265 | 3.2 |
| 向量搜索 | SmartSearchService.java:48-121 | 7.1, 7.3 |
| Embedding生成 | EmbeddingService.java:44-56 | 7.2 |
| 限流 | ResumeService.java:358-365 | 4.2 |
| 数据脱敏 | DataMaskUtil.java:21-26, 36-51 | 9.2 |
| 文件校验 | FileValidationUtil.java | 9.3 |

---

**文档版本**：v1.0
**创建日期**：2026-02-24
**适用范围**：SmartATS项目技术面试
