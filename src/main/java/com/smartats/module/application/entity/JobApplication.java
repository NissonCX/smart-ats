package com.smartats.module.application.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 职位申请实体
 * <p>
 * 对应数据库表 job_applications，是候选人与职位之间的桥梁。
 * 状态流转：PENDING → SCREENING → INTERVIEW → OFFER / REJECTED / WITHDRAWN
 */
@Data
@TableName("job_applications")
public class JobApplication {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 职位 ID（关联 jobs 表）
     */
    private Long jobId;

    /**
     * 候选人 ID（关联 candidates 表）
     */
    private Long candidateId;

    /**
     * AI 匹配分数（0-100）
     */
    private BigDecimal matchScore;

    /**
     * AI 匹配原因（JSON 格式）
     */
    private String matchReasons;

    /**
     * 匹配计算时间
     */
    private LocalDateTime matchCalculatedAt;

    /**
     * 申请状态：PENDING / SCREENING / INTERVIEW / OFFER / REJECTED / WITHDRAWN
     */
    private String status;

    /**
     * HR 备注
     */
    private String hrNotes;

    /**
     * 申请时间
     */
    private LocalDateTime appliedAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
