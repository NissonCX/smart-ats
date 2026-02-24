package com.smartats.module.candidate.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.infrastructure.vector.EmbeddingService;
import com.smartats.infrastructure.vector.VectorStoreService;
import com.smartats.infrastructure.vector.VectorStoreService.SearchResult;
import com.smartats.module.candidate.dto.SmartSearchRequest;
import com.smartats.module.candidate.dto.SmartSearchResponse;
import com.smartats.module.candidate.dto.SmartSearchResponse.MatchedCandidate;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 候选人语义搜索服务
 * <p>
 * 流程：
 * 1. 将查询文本通过 embedding-3 生成向量
 * 2. 在 Milvus 中进行 ANN 搜索，获取 topK 个最相似候选人
 * 3. 按相似度阈值过滤
 * 4. 从 MySQL 获取候选人详细信息
 * 5. 组装响应返回
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartSearchService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final CandidateMapper candidateMapper;

    /**
     * 执行语义搜索
     *
     * @param request 搜索请求
     * @return 搜索结果
     */
    public SmartSearchResponse search(SmartSearchRequest request) {
        log.info("开始语义搜索: query='{}', topK={}, minScore={}",
                request.getQuery(), request.getTopK(), request.getMinScore());

        // 1. 生成查询向量
        List<Float> queryEmbedding;
        try {
            queryEmbedding = embeddingService.generateQueryEmbedding(request.getQuery());
        } catch (Exception e) {
            log.error("查询文本向量化失败: query='{}'", request.getQuery(), e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 向量化服务不可用");
        }

        // 2. Milvus 向量搜索
        List<SearchResult> searchResults;
        try {
            searchResults = vectorStoreService.search(queryEmbedding, request.getTopK());
        } catch (Exception e) {
            log.error("Milvus 向量搜索失败", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "向量搜索服务不可用");
        }

        // 3. 按阈值过滤
        double minScore = request.getMinScore() != null ? request.getMinScore() : 0.3;
        List<SearchResult> filteredResults = searchResults.stream()
                .filter(r -> r.getScore() >= minScore)
                .toList();

        log.info("向量搜索结果: total={}, filtered(minScore={})={}",
                searchResults.size(), minScore, filteredResults.size());

        if (filteredResults.isEmpty()) {
            return buildEmptyResponse(request.getQuery());
        }

        // 4. 批量查询候选人详细信息（避免 N+1）
        List<Long> candidateIds = filteredResults.stream()
                .map(SearchResult::getCandidateId)
                .toList();

        List<Candidate> candidates = candidateMapper.selectBatchIds(candidateIds);
        Map<Long, Candidate> candidateMap = candidates.stream()
                .collect(Collectors.toMap(Candidate::getId, Function.identity()));

        // 5. 组装响应（保持相似度排序）
        List<MatchedCandidate> matchedCandidates = new ArrayList<>();
        for (SearchResult sr : filteredResults) {
            Candidate candidate = candidateMap.get(sr.getCandidateId());
            if (candidate == null) {
                log.warn("候选人在 MySQL 中不存在，可能已被删除: candidateId={}", sr.getCandidateId());
                continue;
            }

            MatchedCandidate mc = new MatchedCandidate();
            mc.setCandidateId(candidate.getId());
            mc.setName(candidate.getName());
            mc.setMatchScore(Math.round(sr.getScore() * 10000.0) / 10000.0);  // 保留4位小数
            mc.setCurrentPosition(candidate.getCurrentPosition());
            mc.setCurrentCompany(candidate.getCurrentCompany());
            mc.setEducation(candidate.getEducation());
            mc.setWorkYears(candidate.getWorkYears());
            mc.setSkills(candidate.getSkills());
            mc.setAiSummary(candidate.getAiSummary());
            matchedCandidates.add(mc);
        }

        SmartSearchResponse response = new SmartSearchResponse();
        response.setQuery(request.getQuery());
        response.setTotalMatches(matchedCandidates.size());
        response.setCandidates(matchedCandidates);

        log.info("语义搜索完成: query='{}', matches={}", request.getQuery(), matchedCandidates.size());
        return response;
    }

    private SmartSearchResponse buildEmptyResponse(String query) {
        SmartSearchResponse response = new SmartSearchResponse();
        response.setQuery(query);
        response.setTotalMatches(0);
        response.setCandidates(List.of());
        return response;
    }
}
