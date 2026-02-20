package com.smartats.module.candidate.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 候选人响应 DTO
 */
@Data
public class CandidateResponse {

    private Long id;
    private Long resumeId;

    // 基本信息
    private String name;
    private String phone;
    private String email;
    private String gender;
    private Integer age;

    // 教育信息
    private String education;
    private String school;
    private String major;
    private Integer graduationYear;

    // 工作信息
    private Integer workYears;
    private String currentCompany;
    private String currentPosition;

    // 技能与经历
    private List<String> skills;
    private List<Map<String, Object>> workExperience;
    private List<Map<String, Object>> projectExperience;
    private String selfEvaluation;

    // 元数据
    private LocalDateTime parsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
