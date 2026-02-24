package com.smartats.module.analytics.controller;

import com.smartats.common.annotation.AuditLog;
import com.smartats.common.result.Result;
import com.smartats.module.analytics.dto.AnalyticsQueryRequest;
import com.smartats.module.analytics.dto.RecruitmentOverviewDTO;
import com.smartats.module.analytics.service.AnalyticsService;
import com.smartats.module.analytics.service.SseEmitterManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 招聘分析控制器
 * <p>
 * 提供两类接口：
 * <ul>
 *   <li>REST API：拉取招聘漏斗、核心指标、趋势数据</li>
 *   <li>SSE 流：实时推送管线变化事件到前端 Dashboard</li>
 * </ul>
 *
 * <p>
 * SSE 使用说明（前端接入）：
 * <pre>
 * const es = new EventSource('/api/v1/analytics/stream');
 * es.addEventListener('analytics_update', (e) => {
 *     const data = JSON.parse(e.data);
 *     // 收到更新事件后重新拉取最新数据
 *     fetchOverview();
 * });
 * es.addEventListener('connected', (e) => {
 *     console.log('SSE 已连接', JSON.parse(e.data));
 * });
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "招聘分析", description = "招聘漏斗、核心指标、趋势分析、SSE 实时推送")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SseEmitterManager sseEmitterManager;

    /**
     * 获取招聘总览数据
     * <p>
     * 包含漏斗分布、核心指标（Offer率/平均匹配分/招聘周期）和月度趋势。
     * 支持按职位 ID 和日期范围筛选。
     * 结果缓存 5 分钟。
     */
    @GetMapping("/overview")
    @Operation(summary = "招聘总览", description = "获取招聘漏斗、核心指标、月度趋势（5分钟缓存）")
    @AuditLog(module = "ANALYTICS", operation = "QUERY_OVERVIEW", description = "查询招聘总览数据")
    public Result<RecruitmentOverviewDTO> getOverview(AnalyticsQueryRequest request) {
        if (request == null) {
            request = new AnalyticsQueryRequest();
        }
        RecruitmentOverviewDTO overview = analyticsService.getOverview(request);
        return Result.success(overview);
    }

    /**
     * SSE 实时推送端点
     * <p>
     * 建立 Server-Sent Events 长连接，当招聘管线发生变化时：
     * <ul>
     *   <li>新申请创建 → 推送 APPLICATION_CREATED 事件</li>
     *   <li>状态流转 → 推送 STATUS_CHANGED 事件</li>
     *   <li>面试安排 → 推送 INTERVIEW_SCHEDULED 事件</li>
     * </ul>
     * 客户端收到事件后可重新拉取 /overview 接口获取最新数据。
     * <p>
     * 连接超时：30 分钟，每 30 秒发送心跳保活。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 实时推送", description = "建立长连接，实时接收招聘管线变化通知")
    public SseEmitter stream() {
        log.info("新 SSE 订阅请求");
        return sseEmitterManager.createEmitter();
    }

    /**
     * 获取当前 SSE 连接数（运维监控用）
     */
    @GetMapping("/sse-status")
    @Operation(summary = "SSE 连接状态", description = "查看当前活跃的 SSE 连接数量")
    public Result<Integer> sseStatus() {
        return Result.success(sseEmitterManager.getActiveCount());
    }
}
