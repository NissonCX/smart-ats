package com.smartats.module.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建职位申请请求 DTO
 */
@Data
public class CreateApplicationRequest {

    /**
     * 职位 ID
     */
    @NotNull(message = "职位 ID 不能为空")
    private Long jobId;

    /**
     * 候选人 ID
     */
    @NotNull(message = "候选人 ID 不能为空")
    private Long candidateId;

    /**
     * HR 备注（可选）
     */
    private String hrNotes;
}
