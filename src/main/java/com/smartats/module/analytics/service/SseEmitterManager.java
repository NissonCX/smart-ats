package com.smartats.module.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.module.analytics.event.AnalyticsUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 长连接管理器
 * <p>
 * 核心职责：
 * <ul>
 *   <li>管理所有已连接的 SseEmitter 实例（线程安全的 CopyOnWriteArrayList）</li>
 *   <li>监听 {@link AnalyticsUpdateEvent}，将更新推送到所有已连接客户端</li>
 *   <li>定时心跳（每 30 秒），防止代理/防火墙断开空闲连接</li>
 *   <li>异常自动清理死连接</li>
 * </ul>
 *
 * <p>
 * 企业最佳实践：
 * <ul>
 *   <li>使用 CopyOnWriteArrayList 保证并发安全（读多写少场景）</li>
 *   <li>心跳机制兼容 Nginx/ALB 等反向代理的 idle timeout</li>
 *   <li>通过 Spring ApplicationEvent 与业务层解耦</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager {

    /**
     * SSE 超时时间：30 分钟
     */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /**
     * 所有活跃的 SSE 连接
     */
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private final ObjectMapper objectMapper;

    /**
     * 创建并注册新的 SSE 连接
     *
     * @return 绑定了生命周期回调的 SseEmitter
     */
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE 连接正常关闭，当前连接数: {}", emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE 连接超时断开，当前连接数: {}", emitters.size());
        });

        emitter.onError(ex -> {
            emitters.remove(emitter);
            log.debug("SSE 连接异常断开: {}，当前连接数: {}", ex.getMessage(), emitters.size());
        });

        emitters.add(emitter);
        log.info("新 SSE 连接建立，当前连接数: {}", emitters.size());

        // 发送初始连接成功事件
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "SSE 连接已建立", "timeout", SSE_TIMEOUT)));
        } catch (IOException e) {
            emitters.remove(emitter);
            log.warn("发送初始事件失败，移除连接");
        }

        return emitter;
    }

    /**
     * 监听分析数据更新事件，推送到所有已连接客户端
     * <p>
     * 使用 @Async 异步执行，避免阻塞业务线程
     */
    @Async("asyncExecutor")
    @EventListener
    public void onAnalyticsUpdate(AnalyticsUpdateEvent event) {
        if (emitters.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "eventType", event.getEventType(),
                "message", event.getMessage(),
                "timestamp", System.currentTimeMillis()
        );

        log.info("推送 SSE 事件: {} -> {} 个客户端", event.getEventType(), emitters.size());
        broadcast("analytics_update", payload);
    }

    /**
     * 心跳机制：每 30 秒发送空注释帧
     * <p>
     * 防止 Nginx、ALB 等反向代理因空闲超时断开连接。
     * SSE 规范中，以冒号开头的行是注释帧，客户端会忽略但连接保持活跃。
     */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }

        log.debug("发送 SSE 心跳，当前连接数: {}", emitters.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                emitters.remove(emitter);
                log.debug("心跳失败，移除死连接");
            }
        }
    }

    /**
     * 广播事件到所有连接
     */
    private void broadcast(String eventName, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(objectMapper.writeValueAsString(data)));
            } catch (Exception e) {
                emitters.remove(emitter);
                log.debug("广播失败，移除死连接: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取当前活跃连接数（监控用）
     */
    public int getActiveCount() {
        return emitters.size();
    }
}
