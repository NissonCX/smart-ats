package com.smartats.module.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeUploadResponse {

    /**
     * 任务ID（前端用于轮询状态）
     */
    private String taskId;

    /**
     * 简历ID
     */
    private Long resumeId;

    /**
     * 文件是否已存在（去重）
     */
    private Boolean duplicated;

    /**
     * 提示信息
     */
    private String message;
}