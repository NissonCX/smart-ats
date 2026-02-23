package com.smartats.module.application.dto;

import lombok.Data;

/**
 * 职位申请分页查询请求 DTO
 */
@Data
public class ApplicationQueryRequest {

    // ========== 分页 ==========

    /** 当前页（从 1 开始） */
    private Integer pageNum = 1;

    /** 每页条数（最大 100） */
    private Integer pageSize = 10;

    // ========== 筛选条件 ==========

    /**
     * 职位 ID（HR 视角：查看某职位的所有申请）
     */
    private Long jobId;

    /**
     * 候选人 ID（查看某候选人的所有申请）
     */
    private Long candidateId;

    /**
     * 申请状态筛选（如：PENDING / SCREENING / INTERVIEW / OFFER / REJECTED / WITHDRAWN）
     */
    private String status;

    // ========== 排序 ==========

    /**
     * 排序字段：applied_at（默认）、match_score、updated_at
     */
    private String orderBy = "applied_at";

    /**
     * 排序方向：asc / desc（默认 desc）
     */
    private String orderDirection = "desc";
}
