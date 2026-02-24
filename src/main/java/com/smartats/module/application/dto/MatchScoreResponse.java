package com.smartats.module.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 匹配打分结果 DTO
 */
@Data
public class MatchScoreResponse {

    /** 申请 ID */
    private Long applicationId;

    /** 职位 ID */
    private Long jobId;

    /** 候选人 ID */
    private Long candidateId;

    /** 综合匹配分数（0-100） */
    private BigDecimal totalScore;

    /** 各维度分数明细 */
    private ScoreBreakdown breakdown;

    /** AI 匹配理由列表 */
    private List<String> matchReasons;

    /** 匹配计算时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime calculatedAt;

    /**
     * 各维度分数明细
     */
    @Data
    public static class ScoreBreakdown {
        /** 向量语义相似度得分（0-100，权重 30%） */
        private BigDecimal semanticScore;
        /** 技能匹配得分（0-100，权重 35%） */
        private BigDecimal skillScore;
        /** 经验匹配得分（0-100，权重 20%） */
        private BigDecimal experienceScore;
        /** 学历匹配得分（0-100，权重 15%） */
        private BigDecimal educationScore;
    }
}
