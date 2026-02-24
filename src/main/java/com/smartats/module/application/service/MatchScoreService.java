package com.smartats.module.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.infrastructure.vector.EmbeddingService;
import com.smartats.infrastructure.vector.VectorStoreService;
import com.smartats.module.application.dto.MatchScoreResponse;
import com.smartats.module.application.entity.JobApplication;
import com.smartats.module.application.mapper.JobApplicationMapper;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 职位-候选人智能匹配打分服务
 * <p>
 * 综合多维度计算候选人与职位的匹配度：
 * <ul>
 *   <li><b>向量语义相似度（30%）</b>：基于 Milvus 余弦相似度，衡量整体画像匹配</li>
 *   <li><b>技能匹配度（35%）</b>：JD 要求技能 vs 候选人技能标签的重合率</li>
 *   <li><b>经验匹配度（20%）</b>：工作年限与职位要求区间的匹配程度</li>
 *   <li><b>学历匹配度（15%）</b>：候选人学历与职位学历要求的匹配</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchScoreService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final JobMapper jobMapper;
    private final CandidateMapper candidateMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final ObjectMapper objectMapper;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 权重配置
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 语义相似度权重 */
    private static final double WEIGHT_SEMANTIC = 0.30;
    /** 技能匹配权重 */
    private static final double WEIGHT_SKILL = 0.35;
    /** 经验匹配权重 */
    private static final double WEIGHT_EXPERIENCE = 0.20;
    /** 学历匹配权重 */
    private static final double WEIGHT_EDUCATION = 0.15;

    /** 学历等级映射（用于比较） */
    private static final Map<String, Integer> EDUCATION_LEVEL = Map.of(
            "不限", 0,
            "大专", 1,
            "本科", 2,
            "硕士", 3,
            "博士", 4
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 公开方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 计算并保存匹配分数
     *
     * @param applicationId 职位申请 ID
     * @return 匹配分数结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MatchScoreResponse calculateAndSave(Long applicationId) {
        log.info("开始计算匹配分数: applicationId={}", applicationId);

        // ① 查询申请记录
        JobApplication application = jobApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ResultCode.APPLICATION_NOT_FOUND);
        }

        // ② 查询职位和候选人
        Job job = jobMapper.selectById(application.getJobId());
        if (job == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "职位不存在");
        }

        Candidate candidate = candidateMapper.selectById(application.getCandidateId());
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "候选人不存在");
        }

        // ③ 多维度打分
        MatchScoreResponse response = calculateScore(job, candidate);
        response.setApplicationId(applicationId);
        response.setJobId(job.getId());
        response.setCandidateId(candidate.getId());
        response.setCalculatedAt(LocalDateTime.now());

        // ④ 持久化到 job_applications 表
        application.setMatchScore(response.getTotalScore());
        application.setMatchCalculatedAt(response.getCalculatedAt());
        try {
            application.setMatchReasons(objectMapper.writeValueAsString(response.getMatchReasons()));
        } catch (JsonProcessingException e) {
            log.warn("序列化匹配原因失败: {}", e.getMessage());
        }
        jobApplicationMapper.updateById(application);

        log.info("匹配分数计算完成: applicationId={}, score={}", applicationId, response.getTotalScore());
        return response;
    }

    /**
     * 异步计算匹配分数（用于创建申请时自动触发）
     */
    @Async("asyncExecutor")
    public void calculateAndSaveAsync(Long applicationId) {
        try {
            calculateAndSave(applicationId);
        } catch (Exception e) {
            log.error("异步计算匹配分数失败: applicationId={}, error={}", applicationId, e.getMessage(), e);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 核心打分逻辑
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 计算候选人与职位的综合匹配分数
     */
    private MatchScoreResponse calculateScore(Job job, Candidate candidate) {
        List<String> reasons = new ArrayList<>();

        // 1. 向量语义相似度（0-100）
        double semanticScore = calculateSemanticScore(job, candidate);
        reasons.add(formatSemanticReason(semanticScore));

        // 2. 技能匹配度（0-100）
        double skillScore = calculateSkillScore(job, candidate, reasons);

        // 3. 经验匹配度（0-100）
        double experienceScore = calculateExperienceScore(job, candidate, reasons);

        // 4. 学历匹配度（0-100）
        double educationScore = calculateEducationScore(job, candidate, reasons);

        // 5. 加权综合分
        double totalScore = semanticScore * WEIGHT_SEMANTIC
                + skillScore * WEIGHT_SKILL
                + experienceScore * WEIGHT_EXPERIENCE
                + educationScore * WEIGHT_EDUCATION;

        // 构建响应
        MatchScoreResponse response = new MatchScoreResponse();
        response.setTotalScore(BigDecimal.valueOf(totalScore).setScale(2, RoundingMode.HALF_UP));
        response.setMatchReasons(reasons);

        MatchScoreResponse.ScoreBreakdown breakdown = new MatchScoreResponse.ScoreBreakdown();
        breakdown.setSemanticScore(BigDecimal.valueOf(semanticScore).setScale(2, RoundingMode.HALF_UP));
        breakdown.setSkillScore(BigDecimal.valueOf(skillScore).setScale(2, RoundingMode.HALF_UP));
        breakdown.setExperienceScore(BigDecimal.valueOf(experienceScore).setScale(2, RoundingMode.HALF_UP));
        breakdown.setEducationScore(BigDecimal.valueOf(educationScore).setScale(2, RoundingMode.HALF_UP));
        response.setBreakdown(breakdown);

        return response;
    }

    /**
     * 向量语义相似度计算
     * <p>
     * 将职位 JD 构建为查询文本 → Embedding → Milvus 搜索该候选人 → 返回余弦相似度
     */
    private double calculateSemanticScore(Job job, Candidate candidate) {
        try {
            // 构建职位文本用于 Embedding
            String jobText = buildJobText(job);

            // 生成查询向量
            List<Float> queryEmbedding = embeddingService.generateQueryEmbedding(jobText);

            // 在 Milvus 中搜索（topK=50），寻找目标候选人
            List<VectorStoreService.SearchResult> results = vectorStoreService.search(queryEmbedding, 50);

            // 从搜索结果中找到目标候选人的分数
            for (VectorStoreService.SearchResult result : results) {
                if (result.getCandidateId().equals(candidate.getId())) {
                    // COSINE 相似度 0~1，转换为 0~100 分
                    return result.getScore() * 100;
                }
            }

            // 候选人不在 top-50，给一个基础分
            log.debug("候选人未在向量搜索 top-50 中: candidateId={}", candidate.getId());
            return 20.0;

        } catch (Exception e) {
            log.warn("语义相似度计算失败，使用默认分数: {}", e.getMessage());
            return 50.0; // 计算失败时给中间分，不影响其他维度
        }
    }

    /**
     * 技能匹配度计算
     * <p>
     * 对比职位 requiredSkills 和候选人 skills 的 Jaccard 相似系数
     */
    private double calculateSkillScore(Job job, Candidate candidate, List<String> reasons) {
        List<String> requiredSkills = parseJsonArray(job.getRequiredSkills());
        List<String> candidateSkills = candidate.getSkills();

        if (requiredSkills == null || requiredSkills.isEmpty()) {
            reasons.add("职位未设置技能要求，技能匹配默认满分");
            return 100.0;
        }

        if (candidateSkills == null || candidateSkills.isEmpty()) {
            reasons.add("候选人无技能标签，技能匹配为零");
            return 0.0;
        }

        // 归一化（转小写）后比较
        Set<String> required = requiredSkills.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toSet());

        Set<String> owned = candidateSkills.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toSet());

        // 计算匹配的技能
        Set<String> matched = new HashSet<>(required);
        matched.retainAll(owned);

        // 计算缺失的技能
        Set<String> missing = new HashSet<>(required);
        missing.removeAll(owned);

        double score = (double) matched.size() / required.size() * 100;

        if (!matched.isEmpty()) {
            reasons.add(String.format("匹配技能 %d/%d: %s", matched.size(), required.size(),
                    String.join(", ", matched)));
        }
        if (!missing.isEmpty()) {
            reasons.add(String.format("缺少技能: %s", String.join(", ", missing)));
        }

        return score;
    }

    /**
     * 经验匹配度计算
     * <p>
     * 判断候选人工作年限是否在职位要求区间内
     */
    private double calculateExperienceScore(Job job, Candidate candidate, List<String> reasons) {
        Integer candidateYears = candidate.getWorkYears();
        Integer minYears = job.getExperienceMin();
        Integer maxYears = job.getExperienceMax();

        if (minYears == null && maxYears == null) {
            reasons.add("职位未设置经验要求，经验匹配默认满分");
            return 100.0;
        }

        if (candidateYears == null) {
            reasons.add("候选人工作年限未知");
            return 50.0; // 未知时给中间分
        }

        int min = minYears != null ? minYears : 0;
        int max = maxYears != null ? maxYears : Integer.MAX_VALUE;

        if (candidateYears >= min && candidateYears <= max) {
            reasons.add(String.format("工作年限 %d 年，匹配要求 %d-%s 年",
                    candidateYears, min, maxYears != null ? String.valueOf(maxYears) : "不限"));
            return 100.0;
        }

        // 超出范围，按差距递减
        int gap;
        if (candidateYears < min) {
            gap = min - candidateYears;
            reasons.add(String.format("工作年限 %d 年，低于要求最低 %d 年（差 %d 年）",
                    candidateYears, min, gap));
        } else {
            gap = candidateYears - max;
            reasons.add(String.format("工作年限 %d 年，超出要求最高 %d 年（超 %d 年）",
                    candidateYears, max, gap));
        }

        // 每差 1 年扣 15 分，最低 10 分
        return Math.max(10.0, 100.0 - gap * 15.0);
    }

    /**
     * 学历匹配度计算
     */
    private double calculateEducationScore(Job job, Candidate candidate, List<String> reasons) {
        String requiredEdu = job.getEducation();
        String candidateEdu = candidate.getEducation();

        if (requiredEdu == null || "不限".equals(requiredEdu)) {
            reasons.add("职位学历不限，学历匹配满分");
            return 100.0;
        }

        if (!StringUtils.hasText(candidateEdu)) {
            reasons.add("候选人学历未知");
            return 50.0;
        }

        int required = EDUCATION_LEVEL.getOrDefault(requiredEdu, 0);
        int actual = EDUCATION_LEVEL.getOrDefault(candidateEdu, 0);

        if (actual >= required) {
            reasons.add(String.format("学历 [%s] 满足要求 [%s]", candidateEdu, requiredEdu));
            return 100.0;
        }

        // 每差一个等级扣 25 分
        int gap = required - actual;
        double score = Math.max(0, 100.0 - gap * 25.0);
        reasons.add(String.format("学历 [%s] 低于要求 [%s]（差 %d 个等级）", candidateEdu, requiredEdu, gap));
        return score;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 辅助方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 构建职位描述文本（用于生成 Embedding）
     */
    private String buildJobText(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append("职位: ").append(job.getTitle()).append("\n");
        if (StringUtils.hasText(job.getDepartment())) {
            sb.append("部门: ").append(job.getDepartment()).append("\n");
        }
        if (StringUtils.hasText(job.getDescription())) {
            sb.append("描述: ").append(job.getDescription()).append("\n");
        }
        if (StringUtils.hasText(job.getRequirements())) {
            sb.append("要求: ").append(job.getRequirements()).append("\n");
        }
        List<String> skills = parseJsonArray(job.getRequiredSkills());
        if (skills != null && !skills.isEmpty()) {
            sb.append("技能: ").append(String.join(", ", skills)).append("\n");
        }
        if (job.getEducation() != null) {
            sb.append("学历: ").append(job.getEducation()).append("\n");
        }
        if (job.getExperienceMin() != null) {
            sb.append("经验: ").append(job.getExperienceMin());
            if (job.getExperienceMax() != null) {
                sb.append("-").append(job.getExperienceMax());
            } else {
                sb.append("+");
            }
            sb.append("年\n");
        }
        return sb.toString();
    }

    /**
     * 解析 JSON 数组字符串为 List<String>
     */
    private List<String> parseJsonArray(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.warn("解析 JSON 数组失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 格式化语义相似度匹配原因
     */
    private String formatSemanticReason(double score) {
        if (score >= 80) return String.format("语义匹配度 %.0f%%（高度匹配）", score);
        if (score >= 60) return String.format("语义匹配度 %.0f%%（中等匹配）", score);
        if (score >= 40) return String.format("语义匹配度 %.0f%%（一般匹配）", score);
        return String.format("语义匹配度 %.0f%%（匹配度较低）", score);
    }
}
