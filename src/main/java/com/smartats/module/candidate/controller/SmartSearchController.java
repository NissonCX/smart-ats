package com.smartats.module.candidate.controller;

import com.smartats.common.result.Result;
import com.smartats.module.candidate.dto.SmartSearchRequest;
import com.smartats.module.candidate.dto.SmartSearchResponse;
import com.smartats.module.candidate.service.SmartSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 候选人智能语义搜索接口
 * <p>
 * 基于 Milvus 向量数据库 + 智谱 embedding-3 模型，
 * 支持自然语言描述搜索匹配候选人。
 */
@Slf4j
@RestController
@RequestMapping("/candidates")
@RequiredArgsConstructor
@Tag(name = "候选人智能搜索", description = "基于向量数据库的语义搜索")
public class SmartSearchController {

    private final SmartSearchService smartSearchService;

    /**
     * 语义搜索候选人
     * <p>
     * 使用自然语言描述搜索最匹配的候选人。
     * 系统会将查询文本转化为向量，在 Milvus 中进行 ANN 近似搜索，
     * 返回按相似度排序的候选人列表。
     * <p>
     * 示例查询：
     * - "3年以上 Java 后端开发，熟悉 Spring Boot 和微服务架构"
     * - "有大厂经验的前端工程师，精通 React 和 TypeScript"
     * - "计算机硕士，有机器学习和深度学习项目经验"
     */
    @PostMapping("/smart-search")
    @Operation(summary = "语义搜索候选人", description = "基于自然语言描述搜索匹配的候选人")
    public Result<SmartSearchResponse> smartSearch(@Valid @RequestBody SmartSearchRequest request) {
        log.info("收到语义搜索请求: query='{}', topK={}", request.getQuery(), request.getTopK());

        SmartSearchResponse response = smartSearchService.search(request);
        return Result.success(response);
    }
}
