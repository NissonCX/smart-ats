package com.smartats.module.candidate.dto;

import lombok.Data;

/**
 * 候选人分页查询请求 DTO
 * <p>
 * 支持关键字搜索 + 多维度高级筛选。
 */
@Data
public class CandidateQueryRequest {

    // ========== 分页 ==========
    /** 当前页（从 1 开始） */
    private int page = 1;

    /** 每页条数（最大 100） */
    private int pageSize = 10;

    // ========== 通用关键字 ==========
    /**
     * 关键字（模糊匹配：姓名 / 邮箱 / 公司 / 职位）
     */
    private String keyword;

    // ========== 精确 / 范围筛选 ==========
    /**
     * 学历筛选（如：本科、硕士、博士；对应 DB ENUM 或字符串）
     * 传空则不过滤
     */
    private String education;

    /**
     * 技能筛选（精确匹配 JSON 数组中的一个技能，如：Java、Python）
     * 底层使用 JSON_CONTAINS；传空则不过滤
     */
    private String skill;

    /**
     * 工作年限下限（含），传 null 则不限制
     */
    private Integer minWorkYears;

    /**
     * 工作年限上限（含），传 null 则不限制
     */
    private Integer maxWorkYears;

    /**
     * 当前职位关键字（模糊匹配），传空则不过滤
     */
    private String currentPosition;
}
