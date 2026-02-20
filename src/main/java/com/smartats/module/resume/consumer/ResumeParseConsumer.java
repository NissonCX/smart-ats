package com.smartats.module.resume.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.smartats.config.RabbitMQConfig;
import com.smartats.module.resume.dto.ResumeParseMessage;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.mapper.ResumeMapper;
import com.smartats.module.webhook.enums.WebhookEventType;
import com.smartats.module.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final ResumeMapper resumeMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;

    private static final String TASK_STATUS_KEY_PREFIX = "task:resume:";
    private static final String LOCK_KEY_PREFIX = "lock:resume:";

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

        try {
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
            // TODO: 使用 Redisson 获取分布式锁
            // 这里简化处理，实际应该使用 Redisson

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

            // 5. 模拟 AI 解析（TODO: 实际调用 Spring AI）
            log.info("开始解析简历: resumeId={}, fileName={}", resumeId, resume.getFileName());

            // 模拟耗时操作
            Thread.sleep(3000);

            // TODO: 实际应该调用 AI 解析并保存到 candidates 表

            // 6. 更新任务状态为 COMPLETED
            updateTaskStatus(taskId, "COMPLETED", 100);

            // 7. 更新简历状态
            resume.setStatus("COMPLETED");
            resumeMapper.updateById(resume);

            log.info("简历解析完成: taskId={}, resumeId={}", taskId, resumeId);

            // 8. 触发 Webhook 事件
            triggerWebhookEvent(WebhookEventType.RESUME_PARSE_COMPLETED, resume, taskId, null);

            // 9. 手动确认消息
            channel.basicAck(deliveryTag, false);

        } catch (InterruptedException e) {
            log.error("简历解析被中断: taskId={}", taskId, e);
            handleFailedTask(taskId, "解析被中断");
            retryOrReject(channel, deliveryTag, message);
        } catch (Exception e) {
            log.error("简历解析失败: taskId={}", taskId, e);
            handleFailedTask(taskId, "解析失败: " + e.getMessage());

            // 获取简历信息用于 Webhook
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume != null) {
                triggerWebhookEvent(WebhookEventType.RESUME_PARSE_FAILED, resume, taskId, e.getMessage());
            }

            retryOrReject(channel, deliveryTag, message);
        }
    }

    /**
     * 触发 Webhook 事件
     */
    private void triggerWebhookEvent(WebhookEventType eventType, Resume resume, String taskId, String errorMessage) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("resumeId", resume.getId());
            data.put("fileName", resume.getFileName());
            data.put("userId", resume.getUserId());
            data.put("fileSize", resume.getFileSize());
            data.put("fileType", resume.getFileType());
            data.put("status", resume.getStatus());

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
            // 重试
            log.info("消息重试: retryCount={}", retryCount);
            // TODO: 重新发送到队列，增加重试次数
            channel.basicNack(deliveryTag, false, true);
        } else {
            // 拒绝，进入死信队列
            log.error("消息重试次数超限，进入死信队列: retryCount={}", retryCount);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}