package com.smartats.module.job.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 缓存淘汰服务（独立 Service 保证 @Async 代理生效）
 * <p>
 * 延迟双删策略的关键：<br>
 * 1. 写操作前先删除缓存<br>
 * 2. 异步延迟一段时间后再次删除（清除并发窗口内被重建的旧缓存）<br>
 * <p>
 * ⚠️ Spring @Async 仅在**跨 Bean 调用**时经过代理生效。
 * 如果 asyncDeleteCache() 放在 JobService 同类中被调用，会变成同步执行。
 * 因此必须抽到独立 Service 中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheEvictionService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 异步延迟删除缓存（延迟双删第二步）
     *
     * @param cacheKey 缓存 Key
     */
    @Async("asyncExecutor")
    public void asyncDeleteCache(String cacheKey) {
        try {
            // 延迟 500ms（大于读请求的平均耗时，确保旧缓存已被写入）
            Thread.sleep(500);

            redisTemplate.delete(cacheKey);
            log.debug("延迟双删完成（第 2 次）：key={}", cacheKey);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("延迟删除缓存被中断：key={}", cacheKey, e);
        }
    }
}
