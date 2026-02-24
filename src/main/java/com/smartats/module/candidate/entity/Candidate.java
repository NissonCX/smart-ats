package com.smartats.module.candidate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartats.common.handler.JsonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 候选人实体
 */
@Data
@TableName("candidates")
public class Candidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 简历ID（1:1 关系）
     */
    private Long resumeId;

    // ========== 基本信息 ==========
    private String name;
    private String phone;
    private String email;
    private String gender;

    /** AI 提取的年龄（存入 birth_year 列） */
    @TableField("birth_year")
    private Integer age;

    // ========== 教育信息 ==========
    /** 最高学历 → 对应 DB 列 highest_education */
    @TableField("highest_education")
    private String education;

    /** 毕业院校 → 对应 DB 列 graduate_school */
    @TableField("graduate_school")
    private String school;

    private String major;

    /** 毕业年份 → 对应 DB 列 graduation_year（驼峰自动映射） */
    private Integer graduationYear;

    // ========== 工作信息 ==========
    /** 工作年限 → 对应 DB 列 experience_years */
    @TableField("experience_years")
    private Integer workYears;

    private String currentCompany;
    private String currentPosition;

    // ========== JSON 字段 ==========
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<String> skills;

    /** 工作经历列表 → 对应 DB 列 work_experiences */
    @TableField(value = "work_experiences", typeHandler = JsonTypeHandler.class)
    private List<Map<String, Object>> workExperience;

    /** 项目经历列表 → 对应 DB 列 project_experience（驼峰自动映射） */
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<Map<String, Object>> projectExperience;

    private String selfEvaluation;

    // ========== AI 解析元数据 ==========
    /** AI 原始提取 JSON → 对应 DB 列 raw_extracted_json */
    @TableField("raw_extracted_json")
    private String rawJson;

    private Double confidenceScore;
    private LocalDateTime parsedAt;

    // ========== 向量搜索相关 ==========
    /** 向量数据库中的记录 ID → 对应 DB 列 vector_id */
    private String vectorId;

    /** AI 生成的候选人摘要（用于嵌入和展示）→ 对应 DB 列 ai_summary */
    private String aiSummary;

    // ========== 审计字段 ==========
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}