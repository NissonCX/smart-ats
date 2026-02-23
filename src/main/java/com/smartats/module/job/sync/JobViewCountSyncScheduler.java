package com.smartats.module.job.sync;

import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 职位浏览量定时同步任务
 * <p>
 * 将 Redis 中的浏览量计数器批量同步到 MySQL 数据库
 * <p>
 * 优化策略：
 * - 使用 Redis 原子计数器（INCR）实时累加浏览量
 * - 定时批量同步到 MySQL（减少数据库写压力）
 * - 最终一致性（允许分钟级延迟）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobViewCountSyncScheduler {

    private final StringRedisTemplate redisTemplate;
    private final JobMapper jobMapper;

    /**
     * 定时同步 Redis 浏览量计数器到数据库
     * <p>
     * 执行频率：每 10 分钟执行一次
     * <p>
     * 执行逻辑：
     * 1. 扫描所有 counter:job:view:* 键
     * 2. 原子性读取并删除计数器（GETDEL 避免丢失增量）
     * 3. 批量更新对应的 jobs 表记录
     */
    @Scheduled(fixedRate = 600000)  // 10 分钟执行一次
    public void syncViewCountToDatabase() {
        long startTime = System.currentTimeMillis();
        log.info("开始同步职位浏览量：Redis → MySQL");

        try {
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 第 1 步：扫描所有职位浏览量计数器
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            String scanPattern = RedisKeyConstants.COUNTER_JOB_VIEW_PREFIX + "*";

            // 使用 SCAN 命令避免阻塞（cursor 方式遍历）
            List<String> counterKeys = redisTemplate.execute((RedisCallback<List<String>>) connection -> {
                List<String> keys = new ArrayList<>();
                ScanOptions options = ScanOptions.scanOptions()
                    .match(scanPattern)
                    .count(100)
                    .build();

                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next()));
                    }
                } catch (Exception e) {
                    log.error("SCAN 扫描失败", e);
                }
                return keys;
            });

            if (counterKeys == null || counterKeys.isEmpty()) {
                log.debug("没有需要同步的浏览量计数器");
                return;
            }

            log.info("发现 {} 个职位浏览量计数器", counterKeys.size());

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 第 2 步：批量读取 Redis 计数器值
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            List<JobUpdateBatch> updateBatches = new ArrayList<>();

            for (String counterKey : counterKeys) {
                // 从 key 中提取 jobId（格式：counter:job:view:{jobId}）
                String jobIdStr = counterKey.substring(RedisKeyConstants.COUNTER_JOB_VIEW_PREFIX.length());

                try {
                    Long jobId = Long.parseLong(jobIdStr);
                    // 使用 GETDEL 原子性读取并删除，避免 GET+DELETE 之间的增量丢失
                    String countStr = redisTemplate.opsForValue().getAndDelete(counterKey);

                    if (countStr != null) {
                        int increment = Integer.parseInt(countStr);
                        updateBatches.add(new JobUpdateBatch(jobId, increment, counterKey));
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的 jobId：key={}", counterKey);
                }
            }

            if (updateBatches.isEmpty()) {
                log.debug("没有有效的浏览量数据需要同步");
                return;
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 第 3 步：批量更新数据库
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            int successCount = 0;
            int failCount = 0;

            for (JobUpdateBatch batch : updateBatches) {
                try {
                    Job job = jobMapper.selectById(batch.jobId());
                    if (job != null) {
                        int currentCount = job.getViewCount() != null ? job.getViewCount() : 0;
                        job.setViewCount(currentCount + batch.increment());
                        jobMapper.updateById(job);
                        successCount++;
                    } else {
                        log.warn("职位不存在，跳过同步：jobId={}", batch.jobId());
                    }
                } catch (Exception e) {
                    log.error("同步失败：jobId={}, increment={}", batch.jobId(), batch.increment(), e);
                    failCount++;
                }
            }

            long costTime = System.currentTimeMillis() - startTime;
            log.info("职位浏览量同步完成：成功 {} 条，失败 {} 条，耗时 {} ms",
                successCount, failCount, costTime);

        } catch (Exception e) {
            log.error("职位浏览量同步异常", e);
        }
    }

    /**
     * 批量更新数据结构
     *
     * @param jobId    职位 ID
     * @param increment 浏览量增量
     * @param counterKey Redis 计数器 Key
     */
    private record JobUpdateBatch(Long jobId, int increment, String counterKey) {}
}
