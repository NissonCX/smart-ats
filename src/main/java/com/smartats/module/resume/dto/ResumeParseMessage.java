package com.smartats.module.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParseMessage {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 简历ID
     */
    private Long resumeId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 文件哈希
     */
    private String fileHash;

    /**
     * 重试次数
     */
    private Integer retryCount = 0;
}