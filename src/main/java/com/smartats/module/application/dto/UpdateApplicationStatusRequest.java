package com.smartats.module.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 更新申请状态请求 DTO
 */
@Data
public class UpdateApplicationStatusRequest {

    /**
     * 目标状态
     * <p>
     * 合法值：SCREENING / INTERVIEW / OFFER / REJECTED / WITHDRAWN
     */
    @NotBlank(message = "目标状态不能为空")
    @Pattern(
            regexp = "SCREENING|INTERVIEW|OFFER|REJECTED|WITHDRAWN",
            message = "状态值不合法，允许值：SCREENING, INTERVIEW, OFFER, REJECTED, WITHDRAWN"
    )
    private String status;

    /**
     * HR 备注（可选，用于记录状态变更原因）
     */
    private String hrNotes;
}
