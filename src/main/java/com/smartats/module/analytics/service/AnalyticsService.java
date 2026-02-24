package com.smartats.module.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.enums.ApplicationStatus;
import com.smartats.common.enums.JobStatus;
import com.smartats.module.analytics.dto.AnalyticsQueryRequest;
import com.smartats.module.analytics.dto.FunnelStageDTO;
import com.smartats.module.analytics.dto.RecruitmentOverviewDTO;
import com.smartats.module.analytics.mapper.AnalyticsMapper;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 招聘分析服务
 * <p>
 * 核心职责：
 * <ul>
 *   <li>招聘漏斗聚合（按状态分组，计算转化率）</li>
 *   <li>核心指标汇总（Offer 率、平均匹配分、平均招聘周期等）</li>
 *   <li>月度趋势统计</li>
 *   <li>Redis 缓存（5 分钟 TTL，Cache-Aside 模式）</li>
 * </ul>
 *
 * <p>
 * 缓存策略：
 * <ul>
 *   <li>分析数据属于聚合统计，实时性要求不如详情接口高</li>
 *   <li>5 分钟 TTL 在数据新鲜度和 DB 压力之间取得平衡</li>
 *   <li>SSE 推送确保前端仍能感知到实时变化（触发客户端重新拉取）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final long CACHE_TTL_SECONDS = 300; // 5 分钟

    private final AnalyticsMapper analyticsMapper;
    private final JobMapper jobMapper;
    private final CandidateMapper candidateMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 漏斗阶段顺序定义（正向流程）
     * <p>
     * 招聘管线的逻辑顺序：投递 → 筛选 → 面试 → Offer
     * REJECTED 和 WITHDRAWN 作为非漏斗阶段单独展示
     */
    private static final List<String> FUNNEL_ORDER = List.of(
            "PENDING", "SCREENING", "INTERVIEW", "OFFER"
    );

    /**
     * 获取招聘总览数据（带缓存）
     *
     * @param request 查询参数（支持按职位和日期范围过滤）
     * @return 招聘全景数据
     */
    public RecruitmentOverviewDTO getOverview(AnalyticsQueryRequest request) {
        // 构造缓存 Key
        String cacheKey = buildCacheKey(request);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                log.debug("命中分析缓存: {}", cacheKey);
                return objectMapper.readValue(cached, RecruitmentOverviewDTO.class);
            } catch (JsonProcessingException e) {
                log.warn("分析缓存反序列化失败，重新计算: {}", e.getMessage());
            }
        }

        // 缓存未命中，执行聚合查询
        RecruitmentOverviewDTO overview = buildOverview(request);

        // 写入缓存
        try {
            String json = objectMapper.writeValueAsString(overview);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("分析数据已缓存: {}", cacheKey);
        } catch (JsonProcessingException e) {
            log.warn("分析数据缓存序列化失败: {}", e.getMessage());
        }

        return overview;
    }

    /**
     * 使指定查询条件的缓存失效
     * <p>
     * 由 SSE 事件触发时调用，确保下次查询拿到最新数据
     */
    public void evictCache(AnalyticsQueryRequest request) {
        String cacheKey = buildCacheKey(request);
        stringRedisTemplate.delete(cacheKey);

        // 同时清除无条件的全局缓存
        String globalKey = buildCacheKey(new AnalyticsQueryRequest());
        stringRedisTemplate.delete(globalKey);
    }

    // ==================== 内部实现 ====================

    /**
     * 构建完整的招聘总览数据
     */
    private RecruitmentOverviewDTO buildOverview(AnalyticsQueryRequest request) {
        LocalDateTime startDateTime = request.getStartDate() != null
                ? request.getStartDate().atStartOfDay() : null;
        LocalDateTime endDateTime = request.getEndDate() != null
                ? request.getEndDate().atTime(LocalTime.MAX) : null;
        Long jobId = request.getJobId();

        // 1. 状态分布统计
        List<Map<String, Object>> statusCounts = analyticsMapper.countGroupByStatus(
                jobId, startDateTime, endDateTime);
        Map<String, Long> statusMap = new LinkedHashMap<>();
        long totalApplications = 0;
        for (Map<String, Object> row : statusCounts) {
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            statusMap.put(status, cnt);
            totalApplications += cnt;
        }

        // 2. 构建漏斗数据
        List<FunnelStageDTO> funnel = buildFunnel(statusMap, totalApplications);

        // 3. 月度趋势
        List<Map<String, Object>> monthCounts = analyticsMapper.countByMonth(
                jobId, startDateTime, endDateTime);
        Map<String, Long> applicationTrend = new LinkedHashMap<>();
        for (Map<String, Object> row : monthCounts) {
            String month = (String) row.get("month");
            long cnt = ((Number) row.get("cnt")).longValue();
            applicationTrend.put(month, cnt);
        }

        // 4. 平均匹配分
        BigDecimal avgMatchScore = analyticsMapper.avgMatchScore(jobId, startDateTime, endDateTime);
        if (avgMatchScore != null) {
            avgMatchScore = avgMatchScore.setScale(1, RoundingMode.HALF_UP);
        }

        // 5. 平均招聘周期
        Double avgDays = analyticsMapper.avgDaysToOffer(jobId, startDateTime, endDateTime);
        if (avgDays != null) {
            avgDays = Math.round(avgDays * 10) / 10.0;
        }

        // 6. 面试数量
        long interviewCount = analyticsMapper.countInterviews(startDateTime, endDateTime);

        // 7. Offer 数量
        long offersExtended = statusMap.getOrDefault("OFFER", 0L);

        // 8. 候选人总数（全局指标，不受 jobId 过滤）
        long totalCandidates = candidateMapper.selectCount(null);

        // 9. 职位统计
        long totalJobs = jobMapper.selectCount(null);
        long openJobs = jobMapper.selectCount(
                new LambdaQueryWrapper<Job>().eq(Job::getStatus, JobStatus.PUBLISHED.getCode()));

        // 10. Offer 转化率
        double offerRate = totalApplications > 0
                ? Math.round(offersExtended * 1000.0 / totalApplications) / 10.0
                : 0.0;

        return RecruitmentOverviewDTO.builder()
                .totalApplications(totalApplications)
                .totalCandidates(totalCandidates)
                .totalJobs(totalJobs)
                .openJobs(openJobs)
                .interviewsScheduled(interviewCount)
                .offersExtended(offersExtended)
                .offerRate(offerRate)
                .avgMatchScore(avgMatchScore)
                .avgDaysToOffer(avgDays)
                .funnel(funnel)
                .applicationTrend(applicationTrend)
                .build();
    }

    /**
     * 构建招聘漏斗
     * <p>
     * 漏斗逻辑：每个阶段累计统计"至少到达该阶段"的申请数量。
     * <ul>
     *   <li>PENDING（已投递）：所有申请</li>
     *   <li>SCREENING（筛选）：除 PENDING 外的所有申请</li>
     *   <li>INTERVIEW（面试）：INTERVIEW + OFFER 状态的申请</li>
     *   <li>OFFER（录用）：仅 OFFER 状态</li>
     * </ul>
     * REJECTED 和 WITHDRAWN 作为附加阶段显示，不参与转化率计算。
     */
    private List<FunnelStageDTO> buildFunnel(Map<String, Long> statusMap, long total) {
        List<FunnelStageDTO> funnel = new ArrayList<>();

        if (total == 0) {
            for (String stage : FUNNEL_ORDER) {
                ApplicationStatus appStatus = ApplicationStatus.fromCode(stage);
                funnel.add(FunnelStageDTO.builder()
                        .stage(stage)
                        .stageLabel(appStatus != null ? appStatus.getDescription() : stage)
                        .count(0)
                        .percentage(0)
                        .conversionRate(0)
                        .build());
            }
            return funnel;
        }

        // 计算累计到达各阶段的数量
        // OFFER 的申请一定经历过 INTERVIEW、SCREENING
        long offerCount = statusMap.getOrDefault("OFFER", 0L);
        long interviewCount = statusMap.getOrDefault("INTERVIEW", 0L) + offerCount;
        long screeningCount = statusMap.getOrDefault("SCREENING", 0L) + interviewCount;
        long pendingCount = total; // 所有申请都经历过投递阶段

        Map<String, Long> cumulativeCounts = new LinkedHashMap<>();
        cumulativeCounts.put("PENDING", pendingCount);
        cumulativeCounts.put("SCREENING", screeningCount);
        cumulativeCounts.put("INTERVIEW", interviewCount);
        cumulativeCounts.put("OFFER", offerCount);

        long previousCount = 0;
        for (String stage : FUNNEL_ORDER) {
            long count = cumulativeCounts.getOrDefault(stage, 0L);
            ApplicationStatus appStatus = ApplicationStatus.fromCode(stage);

            double percentage = Math.round(count * 1000.0 / total) / 10.0;
            double conversionRate;
            if (stage.equals("PENDING")) {
                conversionRate = 100.0;
            } else {
                conversionRate = previousCount > 0
                        ? Math.round(count * 1000.0 / previousCount) / 10.0
                        : 0.0;
            }

            funnel.add(FunnelStageDTO.builder()
                    .stage(stage)
                    .stageLabel(appStatus != null ? appStatus.getDescription() : stage)
                    .count(count)
                    .percentage(percentage)
                    .conversionRate(conversionRate)
                    .build());

            previousCount = count;
        }

        // 附加：REJECTED 和 WITHDRAWN（非漏斗阶段，仅展示绝对数量）
        for (String extra : List.of("REJECTED", "WITHDRAWN")) {
            long count = statusMap.getOrDefault(extra, 0L);
            ApplicationStatus appStatus = ApplicationStatus.fromCode(extra);
            funnel.add(FunnelStageDTO.builder()
                    .stage(extra)
                    .stageLabel(appStatus != null ? appStatus.getDescription() : extra)
                    .count(count)
                    .percentage(total > 0 ? Math.round(count * 1000.0 / total) / 10.0 : 0.0)
                    .conversionRate(0) // 非漏斗阶段不计算转化率
                    .build());
        }

        return funnel;
    }

    /**
     * 构建缓存 Key
     * <p>
     * 格式：cache:analytics:overview:{jobId}:{startDate}:{endDate}
     * 空参数用 "all" 占位
     */
    private String buildCacheKey(AnalyticsQueryRequest request) {
        String jobPart = request.getJobId() != null ? request.getJobId().toString() : "all";
        String startPart = request.getStartDate() != null ? request.getStartDate().toString() : "all";
        String endPart = request.getEndDate() != null ? request.getEndDate().toString() : "all";
        return RedisKeyConstants.CACHE_ANALYTICS_KEY_PREFIX + jobPart + ":" + startPart + ":" + endPart;
    }
}
