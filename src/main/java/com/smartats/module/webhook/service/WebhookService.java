package com.smartats.module.webhook.service;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.module.webhook.dto.WebhookCreateRequest;
import com.smartats.module.webhook.dto.WebhookPayload;
import com.smartats.module.webhook.dto.WebhookResponse;
import com.smartats.module.webhook.entity.WebhookConfig;
import com.smartats.module.webhook.entity.WebhookLog;
import com.smartats.module.webhook.enums.WebhookEventType;
import com.smartats.module.webhook.mapper.WebhookConfigMapper;
import com.smartats.module.webhook.mapper.WebhookLogMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Webhook 服务
 * 负责 Webhook 配置管理和事件发送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookConfigMapper webhookConfigMapper;
    private final WebhookLogMapper webhookLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * HTTP 客户端（带连接池和超时配置）
     */
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 失败计数器（用于自动禁用失败的 Webhook）
     */
    private static final Map<Long, Integer> failureCounters = new ConcurrentHashMap<>();

    /**
     * 最大连续失败次数
     */
    private static final int MAX_FAILURES = 5;

    /**
     * 创建 Webhook 配置
     */
    @Transactional(rollbackFor = Exception.class)
    public WebhookResponse createWebhook(Long userId, WebhookCreateRequest request) {
        // 验证事件类型
        for (String event : request.getEvents()) {
            if (WebhookEventType.fromCode(event) == null) {
                throw new IllegalArgumentException("无效的事件类型: " + event);
            }
        }

        // 生成随机密钥
        String secret = UUID.randomUUID().toString().replace("-", "");

        WebhookConfig config = new WebhookConfig();
        config.setUserId(userId);
        config.setUrl(request.getUrl());
        config.setEvents(String.join(",", request.getEvents()));
        config.setSecret(secret);
        config.setEnabled(true);
        config.setDescription(request.getDescription());
        config.setFailureCount(0);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());

        webhookConfigMapper.insert(config);

        log.info("创建 Webhook 配置: userId={}, webhookId={}, url={}", userId, config.getId(), request.getUrl());

        return toResponse(config);
    }

    /**
     * 获取用户的所有 Webhook 配置
     */
    public List<WebhookResponse> getUserWebhooks(Long userId) {
        LambdaQueryWrapper<WebhookConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WebhookConfig::getUserId, userId);
        wrapper.orderByDesc(WebhookConfig::getCreatedAt);

        List<WebhookConfig> configs = webhookConfigMapper.selectList(wrapper);
        return configs.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 删除 Webhook 配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteWebhook(Long userId, Long webhookId) {
        LambdaQueryWrapper<WebhookConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WebhookConfig::getId, webhookId);
        wrapper.eq(WebhookConfig::getUserId, userId);

        int deleted = webhookConfigMapper.delete(wrapper);

        if (deleted > 0) {
            log.info("删除 Webhook 配置: userId={}, webhookId={}", userId, webhookId);
        }

        failureCounters.remove(webhookId);
    }

    /**
     * 发送 Webhook 事件（异步）
     */
    @Async("webhookExecutor")
    public void sendEvent(WebhookEventType eventType, Map<String, Object> data) {
        String eventCode = eventType.getCode();

        // 查询所有订阅此事件的 Webhook 配置
        List<WebhookConfig> webhooks = findWebhooksByEventType(eventType);

        if (webhooks.isEmpty()) {
            log.debug("没有 Webhook 订阅事件: {}", eventCode);
            return;
        }

        log.info("发送 Webhook 事件: {}, 订阅者数: {}", eventCode, webhooks.size());

        // 并发发送给所有订阅者
        webhooks.forEach(webhook -> {
            try {
                sendWebhook(webhook, eventType, data);
            } catch (Exception e) {
                log.error("发送 Webhook 失败: webhookId={}, event={}", webhook.getId(), eventCode, e);
            }
        });
    }

    /**
     * 发送单个 Webhook
     */
    private void sendWebhook(WebhookConfig webhook, WebhookEventType eventType, Map<String, Object> data) {
        String eventCode = eventType.getCode();
        long startTime = System.currentTimeMillis();

        // 检查是否启用
        if (!webhook.getEnabled()) {
            log.debug("Webhook 已禁用，跳过发送: webhookId={}", webhook.getId());
            return;
        }

        // 检查失败次数
        if (webhook.getFailureCount() != null && webhook.getFailureCount() >= MAX_FAILURES) {
            log.warn("Webhook 失败次数过多，自动禁用: webhookId={}, failureCount={}",
                    webhook.getId(), webhook.getFailureCount());
            webhook.setEnabled(false);
            webhookConfigMapper.updateById(webhook);
            return;
        }

        try {
            // 构建负载
            WebhookPayload payload = buildPayload(eventType, data);

            // 生成签名
            String signature = generateSignature(payload, webhook.getSecret());
            payload.setSignature(signature);

            // 序列化为 JSON
            String jsonPayload = objectMapper.writeValueAsString(payload);

            // 发送 HTTP 请求
            RequestBody body = RequestBody.create(
                    jsonPayload,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(webhook.getUrl())
                    .post(body)
                    .addHeader("X-Webhook-Event", eventCode)
                    .addHeader("X-Webhook-Signature", signature)
                    .addHeader("X-Webhook-ID", payload.getEventId())
                    .addHeader("User-Agent", "SmartATS-Webhook/1.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long duration = System.currentTimeMillis() - startTime;

                if (response.isSuccessful()) {
                    handleSuccess(webhook, eventType, jsonPayload, response, duration);
                } else {
                    handleFailure(webhook, eventType, jsonPayload, response, duration, "HTTP " + response.code());
                }
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            handleFailure(webhook, eventType, null, null, duration, e.getMessage());
        }
    }

    /**
     * 处理发送成功
     */
    private void handleSuccess(WebhookConfig webhook, WebhookEventType eventType,
                               String payload, Response response, long duration) {
        String eventCode = eventType.getCode();

        // 重置失败计数
        webhook.setFailureCount(0);
        webhook.setLastSuccessAt(LocalDateTime.now());
        webhookConfigMapper.updateById(webhook);

        failureCounters.put(webhook.getId(), 0);

        // 记录日志
        WebhookLog log = new WebhookLog();
        log.setWebhookId(webhook.getId());
        log.setEventType(eventCode);
        log.setPayload(payload);
        log.setResponseStatus(response.code());
        log.setStatus("SUCCESS");
        log.setRetryCount(0);
        log.setDuration(duration);
        log.setCreatedAt(LocalDateTime.now());
        webhookLogMapper.insert(log);

        log.info("Webhook 发送成功: webhookId={}, event={}, duration={}ms",
                webhook.getId(), eventCode, duration);
    }

    /**
     * 处理发送失败
     */
    private void handleFailure(WebhookConfig webhook, WebhookEventType eventType,
                               String payload, Response response, long duration, String errorMessage) {
        String eventCode = eventType.getCode();

        // 增加失败计数
        int newFailureCount = (webhook.getFailureCount() == null ? 0 : webhook.getFailureCount()) + 1;
        webhook.setFailureCount(newFailureCount);
        webhook.setLastFailureAt(LocalDateTime.now());

        // 达到阈值，自动禁用
        if (newFailureCount >= MAX_FAILURES) {
            webhook.setEnabled(false);
            log.error("Webhook 失败次数达到阈值，自动禁用: webhookId={}, failures={}",
                    webhook.getId(), newFailureCount);
        }

        webhookConfigMapper.updateById(webhook);
        failureCounters.put(webhook.getId(), newFailureCount);

        // 记录日志
        WebhookLog log = new WebhookLog();
        log.setWebhookId(webhook.getId());
        log.setEventType(eventCode);
        log.setPayload(payload);
        log.setResponseStatus(response != null ? response.code() : null);
        log.setErrorMessage(errorMessage);
        log.setStatus("FAILED");
        log.setRetryCount(0);
        log.setDuration(duration);
        log.setCreatedAt(LocalDateTime.now());
        webhookLogMapper.insert(log);

        log.error("Webhook 发送失败: webhookId={}, event={}, error={}",
                webhook.getId(), eventCode, errorMessage);
    }

    /**
     * 查询订阅指定事件的所有 Webhook
     */
    private List<WebhookConfig> findWebhooksByEventType(WebhookEventType eventType) {
        LambdaQueryWrapper<WebhookConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WebhookConfig::getEnabled, true);
        wrapper.like(WebhookConfig::getEvents, eventType.getCode());
        return webhookConfigMapper.selectList(wrapper);
    }

    /**
     * 构建负载
     */
    private WebhookPayload buildPayload(WebhookEventType eventType, Map<String, Object> data) {
        return WebhookPayload.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType.getCode())
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
    }

    /**
     * 生成签名
     * 使用 HMAC-SHA256 算法
     */
    private String generateSignature(WebhookPayload payload, String secret) {
        try {
            // 将 payload 转换为 JSON（不含 signature 字段）
            String json = objectMapper.writeValueAsString(payload);

            // HMAC-SHA256 签名
            return "sha256=" + SecureUtil.hmacSha256(secret).digestHex(json);
        } catch (Exception e) {
            throw new RuntimeException("生成签名失败", e);
        }
    }

    /**
     * 转换为响应对象
     */
    private WebhookResponse toResponse(WebhookConfig config) {
        List<String> events = Arrays.asList(config.getEvents().split(","));

        // 密钥提示（只显示前 4 位和后 4 位）
        String secretHint = "";
        if (config.getSecret() != null && config.getSecret().length() > 8) {
            secretHint = config.getSecret().substring(0, 4) + "****" +
                    config.getSecret().substring(config.getSecret().length() - 4);
        }

        return WebhookResponse.builder()
                .id(config.getId())
                .url(config.getUrl())
                .events(events)
                .description(config.getDescription())
                .enabled(config.getEnabled())
                .failureCount(config.getFailureCount())
                .lastSuccessAt(config.getLastSuccessAt())
                .lastFailureAt(config.getLastFailureAt())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .secretHint(secretHint)
                .build();
    }

    @PreDestroy
    public void destroy() {
        // 关闭 HTTP 客户端连接池
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
