package com.smartats.module.interview.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安排面试请求 DTO
 */
@Data
public class ScheduleInterviewRequest {

    /**
     * 关联的申请 ID
     */
    @NotNull(message = "申请 ID 不能为空")
    private Long applicationId;

    /**
     * 面试官用户 ID
     */
    @NotNull(message = "面试官 ID 不能为空")
    private Long interviewerId;

    /**
     * 面试轮次（默认自动递增，可手动指定）
     */
    @Min(value = 1, message = "面试轮次最小为 1")
    private Integer round;

    /**
     * 面试类型
     */
    @NotNull(message = "面试类型不能为空")
    @Pattern(
            regexp = "PHONE|VIDEO|ONSITE|WRITTEN_TEST",
            message = "面试类型不合法，允许值：PHONE, VIDEO, ONSITE, WRITTEN_TEST"
    )
    private String interviewType;

    /**
     * 计划面试时间（必须是未来时间）
     */
    @NotNull(message = "面试时间不能为空")
    @Future(message = "面试时间必须是未来时间")
    private LocalDateTime scheduledAt;

    /**
     * 面试时长（分钟，默认 60）
     */
    @Min(value = 15, message = "面试时长最少 15 分钟")
    private Integer durationMinutes = 60;
}
