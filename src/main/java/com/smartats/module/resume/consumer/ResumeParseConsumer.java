package com.smartats.module.resume.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.enums.ResumeStatus;
import com.smartats.config.RabbitMQConfig;
import com.smartats.module.resume.dto.ResumeParseMessage;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.mapper.ResumeMapper;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.service.CandidateService;
import com.smartats.module.candidate.service.CandidateVectorService;
import com.smartats.module.resume.dto.CandidateInfo;
import com.smartats.module.resume.service.ResumeContentExtractor;
import com.smartats.module.resume.service.ResumeParseService;
import com.smartats.module.webhook.enums.WebhookEventType;
import com.smartats.module.webhook.service.WebhookService;
import org.redisson.api.RLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParseConsumer {
    private final ResumeContentExtractor contentExtractor;
    private final ResumeParseService parseService;
    private final CandidateService candidateService;
    private final CandidateVectorService candidateVectorService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;
    private final ResumeMapper resumeMapper;

    private static final String TASK_STATUS_KEY_PREFIX = RedisKeyConstants.RESUME_TASK_KEY_PREFIX;
    private static final String LOCK_KEY_PREFIX = RedisKeyConstants.RESUME_LOCK_KEY_PREFIX;

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
            // 尝试获取锁，最多等待 10 秒，锁自动释放时间 300 秒
            boolean acquired = lock.tryLock(10, 300, java.util.concurrent.TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("获取锁失败，可能有其他实例正在处理: resumeId={}", resumeId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("获取锁成功，开始处理: resumeId={}", resumeId);

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
            log.info("文件内容提取完成: contentLength={}", content.length());
            updateTaskStatus(taskId, "PROCESSING", 30);

            // 6. AI 解析
            log.info("开始 AI 解析: resumeId={}", resumeId);
            ResumeParseService.ParseResult parseResult = parseService.parseResumeWithRaw(content);
            CandidateInfo candidateInfo = parseResult.candidateInfo();
            String rawJson = parseResult.rawResponse();
            log.info("AI 解析完成: name={}, phone={}", candidateInfo.getName(), candidateInfo.getPhone());
            updateTaskStatus(taskId, "PROCESSING", 70);

            // 7. 保存候选人信息
            log.info("保存候选人信息: resumeId={}", resumeId);
            Candidate candidate = candidateService.createCandidate(resumeId, candidateInfo, rawJson);
            log.info("候选人信息保存成功: candidateId={}", candidate.getId());
            updateTaskStatus(taskId, "PROCESSING", 85);

            // 7.5 向量化候选人（生成嵌入并存入 Milvus）
            log.info("开始向量化候选人: candidateId={}", candidate.getId());
            candidateVectorService.vectorizeCandidate(candidate);
            updateTaskStatus(taskId, "PROCESSING", 95);

            // 8. 更新任务状态为 COMPLETED
            updateTaskStatus(taskId, "COMPLETED", 100);

            // 9. 更新简历状态（清除可能残留的错误信息）
            resume.setStatus(ResumeStatus.COMPLETED.getCode());
            resume.setErrorMessage(null);
            resumeMapper.updateById(resume);

            log.info("简历解析完成: taskId={}, resumeId={}, candidateId={}", taskId, resumeId, candidate.getId());

            // 10. 触发 Webhook 事件（传递候选人信息）
            triggerWebhookEvent(WebhookEventType.RESUME_PARSE_COMPLETED, resume, taskId, null, candidate.getId());

            // 11. 手动确认消息
            channel.basicAck(deliveryTag, false);

        } catch (InterruptedException e) {
            int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
            redisTemplate.delete(idempotentKey);

            if (retryCount < 3) {
                log.warn("简历解析被中断，准备第 {} 次重试: taskId={}", retryCount + 1, taskId);
                updateRetryingStatus(taskId, retryCount + 1, 3,
                        String.format("解析被中断，%d秒后第 %d 次重试", getRetryDelay(retryCount) / 1000, retryCount + 1));
            } else {
                log.error("简历解析被中断（重试已耗尽）: taskId={}", taskId, e);
                handleFailedTask(taskId, "解析被中断（已重试3次）");
                updateResumeStatusToFailed(resumeId, "解析被中断");
            }

            retryOrReject(channel, deliveryTag, message);
            Thread.currentThread().interrupt();

        } catch (Exception e) {
            int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
            redisTemplate.delete(idempotentKey);

            if (retryCount < 3) {
                // 还有重试机会 → 标记 RETRYING，不标记最终失败
                long nextDelay = getRetryDelay(retryCount);
                log.warn("简历解析失败，准备第 {} 次重试（{}秒后）: taskId={}, error={}",
                        retryCount + 1, nextDelay / 1000, taskId, e.getMessage());
                updateRetryingStatus(taskId, retryCount + 1, 3,
                        String.format("第 %d 次尝试失败，%d秒后重试: %s",
                                retryCount + 1, nextDelay / 1000, e.getMessage()));
                // DB 保持 PARSING 状态，不更新为 FAILED；不触发失败 Webhook
            } else {
                // 重试已用尽 → 最终失败
                log.error("简历解析最终失败（已重试3次）: taskId={}", taskId, e);
                handleFailedTask(taskId, "解析失败（已重试3次）: " + e.getMessage());

                Resume resume = resumeMapper.selectById(resumeId);
                if (resume != null) {
                    resume.setStatus(ResumeStatus.FAILED.getCode());
                    resume.setErrorMessage(e.getMessage());
                    resumeMapper.updateById(resume);
                    log.info("已更新简历数据库状态为 FAILED: resumeId={}", resumeId);
                    triggerWebhookEvent(WebhookEventType.RESUME_PARSE_FAILED, resume, taskId, e.getMessage(), null);
                }
            }

            retryOrReject(channel, deliveryTag, message);

        } finally {
            // 释放锁（如果当前线程持有锁）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("释放锁成功: lockKey={}", lockKey);
            }
        }
    }

    /**
     * 触发 Webhook 事件
     */
    private void triggerWebhookEvent(WebhookEventType eventType, Resume resume, String taskId, String errorMessage, Long candidateId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("resumeId", resume.getId());
            data.put("fileName", resume.getFileName());
            data.put("userId", resume.getUserId());
            data.put("fileSize", resume.getFileSize());
            data.put("fileType", resume.getFileType());
            data.put("status", resume.getStatus());

            if (candidateId != null) {
                data.put("candidateId", candidateId);
            }

            if (errorMessage != null) {
                data.put("errorMessage", errorMessage);
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

        // 手动序列化为 JSON 字符串
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
     * 更新任务状态为 RETRYING（含重试信息）
     */
    private void updateRetryingStatus(String taskId, int retryCount, int maxRetries, String errorMessage) {
        try {
            String taskKey = TASK_STATUS_KEY_PREFIX + taskId;
            TaskStatusResponse taskStatus = new TaskStatusResponse();
            taskStatus.setStatus("RETRYING");
            taskStatus.setProgress(0);
            taskStatus.setErrorMessage(errorMessage);
            taskStatus.setRetryCount(retryCount);
            taskStatus.setMaxRetries(maxRetries);

            String json = objectMapper.writeValueAsString(taskStatus);
            redisTemplate.opsForValue().set(taskKey, json, 24, java.util.concurrent.TimeUnit.HOURS);
            log.info("更新任务状态: taskId={}, status=RETRYING, retry={}/{}", taskId, retryCount, maxRetries);
        } catch (Exception e) {
            log.error("更新 RETRYING 状态失败: taskId={}", taskId, e);
        }
    }

    /**
     * 更新简历数据库状态为 FAILED（用于中断场景无法获取 resume 对象时）
     */
    private void updateResumeStatusToFailed(Long resumeId, String errorMessage) {
        try {
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume != null) {
                resume.setStatus(ResumeStatus.FAILED.getCode());
                resume.setErrorMessage(errorMessage);
                resumeMapper.updateById(resume);
                log.info("已更新简历数据库状态为 FAILED: resumeId={}", resumeId);
            }
        } catch (Exception e) {
            log.error("更新简历数据库状态失败: resumeId={}", resumeId, e);
        }
    }

    /**
     * 计算指数退避延迟时间（毫秒）
     * retry 1: 10秒, retry 2: 30秒, retry 3: 60秒
     */
    private long getRetryDelay(int retryCount) {
        return switch (retryCount) {
            case 0 -> 10_000L;   // 第1次重试: 10秒
            case 1 -> 30_000L;   // 第2次重试: 30秒
            case 2 -> 60_000L;   // 第3次重试: 60秒
            default -> 60_000L;
        };
    }

    /**
     * 重试或拒绝消息（使用延迟队列实现指数退避）
     */
    private void retryOrReject(Channel channel, long deliveryTag, ResumeParseMessage message) throws IOException {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();

        if (retryCount < 3) {
            // 删除幂等标记，确保重试消息不被跳过
            String idempotentKey = "idempotent:resume:" + message.getResumeId();
            redisTemplate.delete(idempotentKey);

            long delay = getRetryDelay(retryCount);
            log.info("消息重试: retryCount={}, 即将发送第 {} 次重试, 延迟 {}秒", retryCount, retryCount + 1, delay / 1000);
            message.setRetryCount(retryCount + 1);
            try {
                // 发送到延迟队列，设置 per-message TTL 实现指数退避
                // 消息过期后会通过 DLX 自动路由回主队列 resume.parse.queue
                String json = objectMapper.writeValueAsString(message);
                channel.basicPublish(
                        RabbitMQConfig.RESUME_EXCHANGE,
                        RabbitMQConfig.RESUME_PARSE_DELAY_ROUTING_KEY,
                        new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                                .contentType("application/json")
                                .expiration(String.valueOf(delay))  // per-message TTL（毫秒）
                                .build(),
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
            } catch (Exception e) {
                log.error("重试消息发送失败，拒绝进入死信队列: retryCount={}", retryCount, e);
            }
            // ACK 原消息，避免重复消费
            channel.basicAck(deliveryTag, false);
        } else {
            // 拒绝，进入死信队列
            log.error("消息重试次数超限，进入死信队列: retryCount={}", retryCount);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}