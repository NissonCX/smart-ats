package com.smartats.module.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 漏斗阶段数据
 * <p>
 * 表示招聘漏斗中每个阶段的统计信息，包含绝对数量和相对比率，
 * 方便前端渲染漏斗图和转化率分析。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelStageDTO implements Serializable {

    /**
     * 阶段编码（与 ApplicationStatus 枚举对应）
     */
    private String stage;

    /**
     * 阶段中文名称
     */
    private String stageLabel;

    /**
     * 该阶段的申请数量
     */
    private long count;

    /**
     * 占总申请的百分比（0-100.0）
     */
    private double percentage;

    /**
     * 相对上一阶段的转化率（0-100.0），第一阶段为 100.0
     */
    private double conversionRate;
}
