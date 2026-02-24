package com.smartats.infrastructure.vector;

import com.smartats.module.candidate.entity.Candidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

/**
 * 候选人文本嵌入服务
 * <p>
 * 职责：
 * 1. 将候选人结构化信息拼接为可嵌入的文本
 * 2. 调用智谱 embedding-3 模型生成 1024 维向量
 * <p>
 * 嵌入文本构建策略：
 * - 拼接姓名、学历、学校、专业、技能、工作经历、项目经历、自我评价
 * - 文本过长时截断到 6000 字符（embedding-3 最大 8192 token）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /** embedding-3 输出维度 */
    public static final int EMBEDDING_DIMENSION = 1024;

    /** 嵌入文本最大字符长度（留余量，embedding-3 max 8192 token） */
    private static final int MAX_TEXT_LENGTH = 6000;

    /**
     * 为候选人生成嵌入向量
     *
     * @param candidate 候选人实体
     * @return 1024 维浮点向量
     */
    public List<Float> generateCandidateEmbedding(Candidate candidate) {
        String text = buildCandidateText(candidate);
        return embed(text);
    }

    /**
     * 为搜索查询文本生成嵌入向量
     *
     * @param query 搜索查询文本
     * @return 1024 维浮点向量
     */
    public List<Float> generateQueryEmbedding(String query) {
        return embed(query);
    }

    /**
     * 为候选人构建 AI 摘要文本（用于存储到 candidates.ai_summary）
     *
     * @param candidate 候选人实体
     * @return 结构化摘要文本
     */
    public String buildCandidateText(Candidate candidate) {
        StringJoiner joiner = new StringJoiner("\n");

        // 基本信息
        appendIfPresent(joiner, "姓名", candidate.getName());
        appendIfPresent(joiner, "性别", candidate.getGender());

        // 教育信息
        appendIfPresent(joiner, "学历", candidate.getEducation());
        appendIfPresent(joiner, "院校", candidate.getSchool());
        appendIfPresent(joiner, "专业", candidate.getMajor());

        // 工作信息
        if (candidate.getWorkYears() != null) {
            joiner.add("工作年限: " + candidate.getWorkYears() + "年");
        }
        appendIfPresent(joiner, "当前公司", candidate.getCurrentCompany());
        appendIfPresent(joiner, "当前职位", candidate.getCurrentPosition());

        // 技能标签
        if (candidate.getSkills() != null && !candidate.getSkills().isEmpty()) {
            joiner.add("技能: " + String.join(", ", candidate.getSkills()));
        }

        // 工作经历 — 提取公司名和职位
        if (candidate.getWorkExperience() != null) {
            StringBuilder workExp = new StringBuilder("工作经历: ");
            for (var exp : candidate.getWorkExperience()) {
                Object company = exp.get("company");
                Object position = exp.get("position");
                Object description = exp.get("description");
                if (company != null) workExp.append(company);
                if (position != null) workExp.append(" ").append(position);
                if (description != null) workExp.append(" — ").append(description);
                workExp.append("; ");
            }
            joiner.add(workExp.toString().trim());
        }

        // 项目经历 — 提取项目名和描述
        if (candidate.getProjectExperience() != null) {
            StringBuilder projExp = new StringBuilder("项目经历: ");
            for (var exp : candidate.getProjectExperience()) {
                Object name = exp.get("name");
                Object description = exp.get("description");
                Object role = exp.get("role");
                if (name != null) projExp.append(name);
                if (role != null) projExp.append("(").append(role).append(")");
                if (description != null) projExp.append(" — ").append(description);
                projExp.append("; ");
            }
            joiner.add(projExp.toString().trim());
        }

        // 自我评价
        appendIfPresent(joiner, "自我评价", candidate.getSelfEvaluation());

        String text = joiner.toString();

        // 截断保护
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
            log.warn("候选人嵌入文本超长，已截断: candidateId={}, originalLength={}",
                    candidate.getId(), joiner.toString().length());
        }

        return text;
    }

    /**
     * 调用嵌入模型生成向量
     */
    private List<Float> embed(String text) {
        log.debug("生成嵌入向量: textLength={}", text.length());

        EmbeddingResponse response = embeddingModel.call(
                new org.springframework.ai.embedding.EmbeddingRequest(
                        List.of(text),
                        org.springframework.ai.openai.OpenAiEmbeddingOptions.builder()
                                .build()
                )
        );

        float[] embedding = response.getResult().getOutput();
        List<Float> result = new java.util.ArrayList<>(embedding.length);
        for (float v : embedding) {
            result.add(v);
        }

        log.debug("嵌入向量生成完成: dimension={}", result.size());
        return result;
    }

    private void appendIfPresent(StringJoiner joiner, String label, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(label + ": " + value);
        }
    }
}
