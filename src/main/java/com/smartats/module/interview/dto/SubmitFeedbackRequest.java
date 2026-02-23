package com.smartats.module.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 提交面试反馈请求 DTO
 */
@Data
public class SubmitFeedbackRequest {

    /**
     * 面试评分（1-10 分）
     */
    @Min(value = 1, message = "评分最低为 1")
    @Max(value = 10, message = "评分最高为 10")
    private Integer score;

    /**
     * 面试反馈评语
     */
    @NotBlank(message = "面试反馈不能为空")
    private String feedback;

    /**
     * 推荐结论
     */
    @NotBlank(message = "推荐结论不能为空")
    @Pattern(
            regexp = "STRONG_YES|YES|NEUTRAL|NO|STRONG_NO",
            message = "推荐结论不合法，允许值：STRONG_YES, YES, NEUTRAL, NO, STRONG_NO"
    )
    private String recommendation;
}
