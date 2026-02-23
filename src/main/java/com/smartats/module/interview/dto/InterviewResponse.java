package com.smartats.module.interview.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试记录响应 DTO
 */
@Data
public class InterviewResponse {

    private Long id;

    // ========== 关联信息 ==========

    private Long applicationId;

    /** 面试官用户 ID */
    private Long interviewerId;

    /** 面试官姓名（关联查询） */
    private String interviewerName;

    // ========== 面试安排 ==========

    /** 面试轮次 */
    private Integer round;

    /** 面试类型：PHONE / VIDEO / ONSITE / WRITTEN_TEST */
    private String interviewType;

    /** 面试类型中文描述 */
    private String interviewTypeDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledAt;

    /** 面试时长（分钟） */
    private Integer durationMinutes;

    // ========== 面试状态与结果 ==========

    /** 面试状态：SCHEDULED / COMPLETED / CANCELLED / NO_SHOW */
    private String status;

    /** 状态中文描述 */
    private String statusDesc;

    /** 面试反馈评语 */
    private String feedback;

    /** 面试评分（1-10） */
    private Integer score;

    /** 推荐结论 */
    private String recommendation;

    /** 推荐结论中文描述 */
    private String recommendationDesc;

    // ========== 元数据 ==========

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
