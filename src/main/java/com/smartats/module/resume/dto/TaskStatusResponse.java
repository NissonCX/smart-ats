package com.smartats.module.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusResponse {

    /**
     * 任务状态
     */
    private String status;

    /**
     * 简历ID（解析完成后有值）
     */
    private Long resumeId;

    /**
     * 候选人ID（解析完成后有值）
     */
    private Long candidateId;

    /**
     * 错误信息（失败时有值）
     */
    private String errorMessage;

    /**
     * 进度百分比（0-100）
     */
    private Integer progress;

    /**
     * 当前重试次数（RETRYING 状态时有值）
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;
}