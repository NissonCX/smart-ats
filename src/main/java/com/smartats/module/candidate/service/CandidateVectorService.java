package com.smartats.module.candidate.service;

import com.smartats.infrastructure.vector.EmbeddingService;
import com.smartats.infrastructure.vector.VectorStoreService;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 候选人向量化服务
 * <p>
 * 职责：
 * 1. 在候选人创建/更新后，生成嵌入向量并存入 Milvus
 * 2. 在候选人删除时，同步删除 Milvus 中的向量
 * 3. 更新 candidates 表的 vector_id 和 ai_summary 字段
 * <p>
 * 向量化流程：
 *   Candidate → buildCandidateText → embedding-3 → 1024 维向量 → Milvus upsert
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateVectorService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final CandidateMapper candidateMapper;

    /**
     * 为候选人生成向量并存入 Milvus（同步调用，用于解析管线中）
     *
     * @param candidate 已持久化的候选人实体
     */
    public void vectorizeCandidate(Candidate candidate) {
        log.info("开始向量化候选人: candidateId={}, name={}", candidate.getId(), candidate.getName());

        try {
            // 1. 构建摘要文本
            String aiSummary = embeddingService.buildCandidateText(candidate);

            // 2. 生成嵌入向量
            List<Float> embedding = embeddingService.generateCandidateEmbedding(candidate);

            // 3. 存入 Milvus
            String vectorId = vectorStoreService.upsertVector(
                    candidate.getId(),
                    candidate.getName(),
                    embedding
            );

            // 4. 回写 MySQL（更新 vector_id 和 ai_summary）
            Candidate update = new Candidate();
            update.setId(candidate.getId());
            update.setVectorId(vectorId);
            update.setAiSummary(aiSummary);
            update.setUpdatedAt(LocalDateTime.now());
            candidateMapper.updateById(update);

            log.info("候选人向量化完成: candidateId={}, vectorId={}", candidate.getId(), vectorId);

        } catch (Exception e) {
            // 向量化失败不应阻断主流程，记录错误后跳过
            log.error("候选人向量化失败（不影响主流程）: candidateId={}", candidate.getId(), e);
        }
    }

    /**
     * 异步向量化（用于候选人手动更新场景）
     * <p>
     * ⚠ 注意：@Async 方法必须在不同 Bean 中调用才能生效（Spring AOP 代理机制）。
     *
     * @param candidate 已更新的候选人实体
     */
    @Async("asyncExecutor")
    public void vectorizeCandidateAsync(Candidate candidate) {
        vectorizeCandidate(candidate);
    }

    /**
     * 删除候选人向量（同步）
     *
     * @param candidateId 候选人 ID
     */
    public void deleteVector(Long candidateId) {
        try {
            vectorStoreService.deleteVector(candidateId);
            log.info("候选人向量删除成功: candidateId={}", candidateId);
        } catch (Exception e) {
            log.error("候选人向量删除失败（不影响主流程）: candidateId={}", candidateId, e);
        }
    }
}
