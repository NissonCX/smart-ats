package com.smartats.module.candidate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 语义搜索请求 DTO
 * <p>
 * 用于 POST /candidates/smart-search 接口。
 * 支持自然语言查询，如：「3年以上 Java 后端，熟悉 Spring Boot 和微服务」。
 */
@Data
public class SmartSearchRequest {

    /**
     * 搜索查询文本（自然语言描述）
     * <p>
     * 示例：
     * - "3年以上 Java 后端开发，熟悉 Spring Boot"
     * - "有大厂实习经验的应届生，会 Python 和机器学习"
     * - "资深前端工程师，React + TypeScript"
     */
    @NotBlank(message = "搜索查询不能为空")
    @Size(max = 1000, message = "搜索查询文本最长 1000 字符")
    private String query;

    /**
     * 返回最相似的候选人数量（默认 10，最大 50）
     */
    @Min(value = 1, message = "topK 最小为 1")
    @Max(value = 50, message = "topK 最大为 50")
    private Integer topK = 10;

    /**
     * 最低相似度阈值（0~1，低于此分数的结果将被过滤）
     * <p>
     * COSINE 相似度：0 = 完全不相关，1 = 完全匹配。
     * 默认 0.3，过滤明显不相关的结果。
     */
    @Min(value = 0, message = "最低分数不能小于 0")
    @Max(value = 1, message = "最低分数不能大于 1")
    private Double minScore = 0.3;
}
