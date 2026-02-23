package com.smartats.module.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 职位申请响应 DTO
 */
@Data
public class ApplicationResponse {

    private Long id;

    // ========== 关联信息 ==========

    private Long jobId;

    /** 职位标题（方便前端展示，无需二次请求） */
    private String jobTitle;

    private Long candidateId;

    /** 候选人姓名（方便前端展示） */
    private String candidateName;

    // ========== AI 匹配信息 ==========

    /** AI 匹配分数（0-100） */
    private BigDecimal matchScore;

    /** AI 匹配原因 */
    private List<String> matchReasons;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime matchCalculatedAt;

    // ========== 状态与备注 ==========

    /** 申请状态：PENDING / SCREENING / INTERVIEW / OFFER / REJECTED / WITHDRAWN */
    private String status;

    /** 状态中文描述 */
    private String statusDesc;

    /** HR 备注 */
    private String hrNotes;

    // ========== 元数据 ==========

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appliedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
