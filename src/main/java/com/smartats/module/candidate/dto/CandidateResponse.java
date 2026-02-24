package com.smartats.module.candidate.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 候选人响应 DTO
 * <p>
 * 手机号和邮箱均为脱敏版本，不暴露原始敏感数据。
 */
@Data
public class CandidateResponse {

    private Long id;
    private Long resumeId;

    // ========== 基本信息 ==========
    private String name;

    /** 脱敏手机号，如：138****5678 */
    private String phone;

    /** 脱敏邮箱，如：zh****@example.com */
    private String email;

    private String gender;
    private Integer age;

    // ========== 教育信息 ==========
    private String education;
    private String school;
    private String major;
    private Integer graduationYear;

    // ========== 工作信息 ==========
    private Integer workYears;
    private String currentCompany;
    private String currentPosition;

    // ========== 技能与经历 ==========
    private List<String> skills;
    private List<Map<String, Object>> workExperience;
    private List<Map<String, Object>> projectExperience;
    private String selfEvaluation;

    /** AI 解析置信度（0-1） */
    private Double confidenceScore;

    /** AI 生成的候选人摘要 */
    private String aiSummary;

    // ========== 元数据 ==========
    private LocalDateTime parsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

