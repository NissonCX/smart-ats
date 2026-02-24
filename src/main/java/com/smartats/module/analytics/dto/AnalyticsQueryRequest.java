package com.smartats.module.analytics.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 分析查询请求参数
 * <p>
 * 支持按职位 ID、日期范围进行筛选。
 * 所有字段均为可选，不传则查询全部数据。
 */
@Data
@Schema(description = "招聘分析查询参数")
public class AnalyticsQueryRequest {

    @Schema(description = "职位 ID（筛选特定职位的分析数据）")
    private Long jobId;

    @Schema(description = "起始日期（含）", example = "2026-01-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Schema(description = "截止日期（含）", example = "2026-12-31")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
