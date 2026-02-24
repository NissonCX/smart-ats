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
            updateTaskStatus(taskId, "PROCESSING", 90);

            // 8. 更新任务状态为 COMPLETED
            updateTaskStatus(taskId, "COMPLETED", 100);

            // 9. 更新简历状态
            resume.setStatus(ResumeStatus.COMPLETED.getCode());
            resumeMapper.updateById(resume);

            log.info("简历解析完成: taskId={}, resumeId={}, candidateId={}", taskId, resumeId, candidate.getId());

            // 10. 触发 Webhook 事件（传递候选人信息）
            triggerWebhookEvent(WebhookEventType.RESUME_PARSE_COMPLETED, resume, taskId, null, candidate.getId());

            // 11. 手动确认消息
            channel.basicAck(deliveryTag, false);

        } catch (InterruptedException e) {
            log.error("简历解析被中断: taskId={}", taskId, e);
            handleFailedTask(taskId, "解析被中断");
            retryOrReject(channel, deliveryTag, message);

            // 恢复中断状态
            Thread.currentThread().interrupt();

        } catch (Exception e) {
            log.error("简历解析失败: taskId={}", taskId, e);
            handleFailedTask(taskId, "解析失败: " + e.getMessage());

            // 获取简历信息用于 Webhook
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume != null) {
                triggerWebhookEvent(WebhookEventType.RESUME_PARSE_FAILED, resume, taskId, e.getMessage(), null);
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
     * 重试或拒绝消息
     */
    private void retryOrReject(Channel channel, long deliveryTag, ResumeParseMessage message) throws IOException {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();

        if (retryCount < 3) {
            // 重新发布消息到队列，递增重试计数（避免 basicNack requeue 导致无限循环）
            log.info("消息重试: retryCount={}, 即将发送第 {} 次重试", retryCount, retryCount + 1);
            message.setRetryCount(retryCount + 1);
            try {
                // 通过 MessagePublisher 重新发送（带递增的 retryCount）
                String json = objectMapper.writeValueAsString(message);
                channel.basicPublish(
                        com.smartats.config.RabbitMQConfig.RESUME_EXCHANGE,
                        com.smartats.config.RabbitMQConfig.RESUME_PARSE_ROUTING_KEY,
                        new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                                .contentType("application/json")
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