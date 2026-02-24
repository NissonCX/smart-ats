package com.smartats.module.candidate.dto;

import lombok.Data;

import java.util.List;

/**
 * 语义搜索响应 DTO
 */
@Data
public class SmartSearchResponse {

    /** 原始查询文本 */
    private String query;

    /** 返回的匹配候选人数量 */
    private int totalMatches;

    /** 匹配的候选人列表（按相似度倒序） */
    private List<MatchedCandidate> candidates;

    /**
     * 单个匹配候选人结果
     */
    @Data
    public static class MatchedCandidate {

        /** 候选人 ID */
        private Long candidateId;

        /** 候选人姓名 */
        private String name;

        /** 相似度分数（0~1，COSINE） */
        private double matchScore;

        /** 当前职位 */
        private String currentPosition;

        /** 当前公司 */
        private String currentCompany;

        /** 最高学历 */
        private String education;

        /** 工作年限 */
        private Integer workYears;

        /** 技能标签 */
        private List<String> skills;

        /** AI 生成摘要 */
        private String aiSummary;
    }
}
