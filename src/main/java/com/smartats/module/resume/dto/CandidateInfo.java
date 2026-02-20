package com.smartats.module.resume.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 解析的候选人信息 DTO
 * 与智谱 AI 返回的 JSON 结构对应
 */
@Data
public class CandidateInfo {

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
    private List<WorkExperience> workExperience;
    private List<ProjectExperience> projectExperience;
    private String selfEvaluation;

    @Data
    public static class WorkExperience {
        private String company;
        private String position;
        private String startDate;
        private String endDate;
        private String description;
    }

    @Data
    public static class ProjectExperience {
        private String name;
        private String role;
        private String startDate;
        private String endDate;
        private String description;
        private List<String> technologies;
    }
}