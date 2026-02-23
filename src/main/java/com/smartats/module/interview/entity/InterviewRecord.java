package com.smartats.module.interview.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试记录实体
 * <p>
 * 对应数据库表 interview_records，与职位申请(job_applications)为多对一关系——
 * 同一申请可有多轮面试。
 */
@Data
@TableName("interview_records")
public class InterviewRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的申请 ID（job_applications.id）
     */
    private Long applicationId;

    /**
     * 面试官用户 ID（users.id）
     */
    private Long interviewerId;

    /**
     * 面试轮次（1 = 一面，2 = 二面 ...）
     */
    private Integer round;

    /**
     * 面试类型：PHONE / VIDEO / ONSITE / WRITTEN_TEST
     */
    private String interviewType;

    /**
     * 计划面试时间
     */
    private LocalDateTime scheduledAt;

    /**
     * 面试时长（分钟）
     */
    private Integer durationMinutes;

    /**
     * 面试状态：SCHEDULED / COMPLETED / CANCELLED / NO_SHOW
     */
    private String status;

    /**
     * 面试反馈评语
     */
    private String feedback;

    /**
     * 面试评分（1-10）
     */
    private Integer score;

    /**
     * 面试推荐结论：STRONG_YES / YES / NEUTRAL / NO / STRONG_NO
     */
    private String recommendation;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
