package com.smartats.module.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 招聘总览数据
 * <p>
 * 提供完整的招聘管线（Pipeline）全景视图，包括：
 * <ul>
 *   <li>核心指标：总申请、总候选人、Offer 转化率、平均匹配分、平均招聘周期</li>
 *   <li>漏斗分布：各阶段数量及转化率</li>
 *   <li>月度趋势：按月聚合的申请量走势</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentOverviewDTO implements Serializable {

    // ==================== 核心指标 ====================

    /**
     * 总申请数
     */
    private long totalApplications;

    /**
     * 总候选人数
     */
    private long totalCandidates;

    /**
     * 总职位数
     */
    private long totalJobs;

    /**
     * 在招职位数
     */
    private long openJobs;

    /**
     * 已安排面试数
     */
    private long interviewsScheduled;

    /**
     * 已发 Offer 数
     */
    private long offersExtended;

    /**
     * Offer 转化率（总 Offer / 总申请）
     */
    private double offerRate;

    /**
     * 平均 AI 匹配分（0-100）
     */
    private BigDecimal avgMatchScore;

    /**
     * 平均招聘周期（天）：从申请到发出 Offer 的平均天数
     */
    private Double avgDaysToOffer;

    // ==================== 漏斗分布 ====================

    /**
     * 招聘漏斗各阶段统计
     */
    private List<FunnelStageDTO> funnel;

    // ==================== 趋势数据 ====================

    /**
     * 按月聚合的申请趋势 {月份 → 申请数}
     * <p>
     * key 格式: yyyy-MM (如 "2026-01")
     */
    private Map<String, Long> applicationTrend;
}
