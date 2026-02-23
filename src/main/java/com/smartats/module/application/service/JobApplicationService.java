package com.smartats.module.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.application.dto.*;
import com.smartats.module.application.entity.JobApplication;
import com.smartats.module.application.mapper.JobApplicationMapper;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import com.smartats.module.webhook.enums.WebhookEventType;
import com.smartats.module.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 职位申请服务
 * <p>
 * 核心业务逻辑：
 * <ul>
 *   <li>创建申请（防重复、校验职位/候选人）</li>
 *   <li>状态流转（合法性校验 + Webhook 通知）</li>
 *   <li>多维度查询（按职位、按候选人、分页筛选）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationMapper jobApplicationMapper;
    private final JobMapper jobMapper;
    private final CandidateMapper candidateMapper;
    private final WebhookService webhookService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 缓存 TTL（分钟） */
    private static final long CACHE_TTL_MINUTES = 30;

    /**
     * 合法的状态流转映射
     * <p>
     * Key: 当前状态, Value: 允许跳转的目标状态集合
     */
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "PENDING",    Set.of("SCREENING", "REJECTED", "WITHDRAWN"),
            "SCREENING",  Set.of("INTERVIEW", "REJECTED", "WITHDRAWN"),
            "INTERVIEW",  Set.of("OFFER", "REJECTED", "WITHDRAWN"),
            "OFFER",      Set.of("WITHDRAWN"),
            "REJECTED",   Set.of(),
            "WITHDRAWN",  Set.of()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 创建申请
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 创建职位申请
     *
     * @param request 创建请求
     * @return 新建申请的 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createApplication(CreateApplicationRequest request) {
        Long jobId = request.getJobId();
        Long candidateId = request.getCandidateId();

        log.info("创建职位申请：jobId={}, candidateId={}", jobId, candidateId);

        // ① 校验职位是否存在且已发布
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "职位不存在");
        }
        if (!"PUBLISHED".equals(job.getStatus())) {
            throw new BusinessException(ResultCode.APPLICATION_JOB_NOT_PUBLISHED);
        }

        // ② 校验候选人是否存在
        Candidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "候选人不存在");
        }

        // ③ 防重复：同一候选人不能重复申请同一职位
        LambdaQueryWrapper<JobApplication> dupCheck = new LambdaQueryWrapper<>();
        dupCheck.eq(JobApplication::getJobId, jobId)
                .eq(JobApplication::getCandidateId, candidateId);
        if (jobApplicationMapper.selectCount(dupCheck) > 0) {
            throw new BusinessException(ResultCode.APPLICATION_DUPLICATE);
        }

        // ④ 创建申请记录
        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setCandidateId(candidateId);
        application.setStatus("PENDING");
        application.setHrNotes(request.getHrNotes());
        application.setAppliedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());

        jobApplicationMapper.insert(application);

        log.info("职位申请创建成功：id={}, jobId={}, candidateId={}", application.getId(), jobId, candidateId);

        // ⑤ 触发 Webhook 事件
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("applicationId", application.getId());
        eventData.put("jobId", jobId);
        eventData.put("jobTitle", job.getTitle());
        eventData.put("candidateId", candidateId);
        eventData.put("candidateName", candidate.getName());
        eventData.put("status", "PENDING");
        webhookService.sendEvent(WebhookEventType.APPLICATION_SUBMITTED, eventData);

        return application.getId();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 更新状态
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 更新申请状态
     *
     * @param id      申请 ID
     * @param request 状态更新请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, UpdateApplicationStatusRequest request) {
        log.info("更新申请状态：id={}, targetStatus={}", id, request.getStatus());

        JobApplication application = jobApplicationMapper.selectById(id);
        if (application == null) {
            throw new BusinessException(ResultCode.APPLICATION_NOT_FOUND);
        }

        String currentStatus = application.getStatus();
        String targetStatus = request.getStatus();

        // 校验状态流转合法性
        Set<String> allowedTargets = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTargets.contains(targetStatus)) {
            throw new BusinessException(ResultCode.APPLICATION_STATUS_INVALID,
                    String.format("不允许从 [%s] 变更为 [%s]，允许的目标状态：%s",
                            currentStatus, targetStatus, allowedTargets));
        }

        // 更新状态
        application.setStatus(targetStatus);
        application.setUpdatedAt(LocalDateTime.now());
        if (StringUtils.hasText(request.getHrNotes())) {
            application.setHrNotes(request.getHrNotes());
        }

        jobApplicationMapper.updateById(application);
        evictCache(id);

        log.info("申请状态更新成功：id={}, {} → {}", id, currentStatus, targetStatus);

        // 触发 Webhook 事件
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("applicationId", id);
        eventData.put("jobId", application.getJobId());
        eventData.put("candidateId", application.getCandidateId());
        eventData.put("previousStatus", currentStatus);
        eventData.put("currentStatus", targetStatus);
        eventData.put("hrNotes", application.getHrNotes());
        webhookService.sendEvent(WebhookEventType.APPLICATION_STATUS_CHANGED, eventData);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 查询
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 获取申请详情（Redis 缓存）
     */
    public ApplicationResponse getById(Long id) {
        log.info("查询申请详情：id={}", id);

        String cacheKey = RedisKeyConstants.CACHE_APPLICATION_KEY_PREFIX + id;

        // 1. 查缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ApplicationResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("申请缓存反序列化失败，降级查库：id={}", id, e);
            }
        }

        // 2. 查数据库
        JobApplication application = jobApplicationMapper.selectById(id);
        if (application == null) {
            throw new BusinessException(ResultCode.APPLICATION_NOT_FOUND);
        }

        ApplicationResponse response = convertToResponse(application);

        // 3. 回填缓存
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(response),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            log.warn("申请写入缓存失败（不影响业务）：id={}", id, e);
        }

        return response;
    }

    /**
     * 按职位 ID 查询申请列表（HR 视角）
     */
    public Page<ApplicationResponse> listByJobId(Long jobId, Integer pageNum, Integer pageSize) {
        log.info("查询职位申请列表：jobId={}, page={}", jobId, pageNum);

        Page<JobApplication> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<JobApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobApplication::getJobId, jobId)
                .orderByDesc(JobApplication::getAppliedAt);

        Page<JobApplication> resultPage = jobApplicationMapper.selectPage(page, wrapper);
        return convertPage(resultPage);
    }

    /**
     * 按候选人 ID 查询申请列表
     */
    public Page<ApplicationResponse> listByCandidateId(Long candidateId, Integer pageNum, Integer pageSize) {
        log.info("查询候选人申请列表：candidateId={}, page={}", candidateId, pageNum);

        Page<JobApplication> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<JobApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobApplication::getCandidateId, candidateId)
                .orderByDesc(JobApplication::getAppliedAt);

        Page<JobApplication> resultPage = jobApplicationMapper.selectPage(page, wrapper);
        return convertPage(resultPage);
    }

    /**
     * 综合查询（支持多维筛选 + 排序 + 分页）
     */
    public Page<ApplicationResponse> listApplications(ApplicationQueryRequest request) {
        log.info("综合查询申请列表：{}", request);

        int pageNum = Math.max(1, request.getPageNum());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize()));

        Page<JobApplication> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<JobApplication> wrapper = new LambdaQueryWrapper<>();

        // 筛选条件
        if (request.getJobId() != null) {
            wrapper.eq(JobApplication::getJobId, request.getJobId());
        }
        if (request.getCandidateId() != null) {
            wrapper.eq(JobApplication::getCandidateId, request.getCandidateId());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(JobApplication::getStatus, request.getStatus());
        }

        // 排序
        boolean asc = "asc".equalsIgnoreCase(request.getOrderDirection());
        switch (request.getOrderBy() != null ? request.getOrderBy() : "applied_at") {
            case "match_score" -> {
                if (asc) wrapper.orderByAsc(JobApplication::getMatchScore);
                else wrapper.orderByDesc(JobApplication::getMatchScore);
            }
            case "updated_at" -> {
                if (asc) wrapper.orderByAsc(JobApplication::getUpdatedAt);
                else wrapper.orderByDesc(JobApplication::getUpdatedAt);
            }
            default -> {
                if (asc) wrapper.orderByAsc(JobApplication::getAppliedAt);
                else wrapper.orderByDesc(JobApplication::getAppliedAt);
            }
        }

        Page<JobApplication> resultPage = jobApplicationMapper.selectPage(page, wrapper);
        return convertPage(resultPage);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 私有方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 将实体转换为响应 DTO，关联查询职位标题和候选人姓名
     */
    private ApplicationResponse convertToResponse(JobApplication application) {
        ApplicationResponse response = new ApplicationResponse();
        response.setId(application.getId());
        response.setJobId(application.getJobId());
        response.setCandidateId(application.getCandidateId());
        response.setMatchScore(application.getMatchScore());
        response.setMatchCalculatedAt(application.getMatchCalculatedAt());
        response.setStatus(application.getStatus());
        response.setStatusDesc(getStatusDesc(application.getStatus()));
        response.setHrNotes(application.getHrNotes());
        response.setAppliedAt(application.getAppliedAt());
        response.setUpdatedAt(application.getUpdatedAt());

        // JSON 反序列化 matchReasons
        if (StringUtils.hasText(application.getMatchReasons())) {
            try {
                List<String> reasons = objectMapper.readValue(
                        application.getMatchReasons(), new TypeReference<>() {});
                response.setMatchReasons(reasons);
            } catch (JsonProcessingException e) {
                log.warn("matchReasons 反序列化失败：id={}", application.getId(), e);
            }
        }

        // 关联查询职位标题
        Job job = jobMapper.selectById(application.getJobId());
        if (job != null) {
            response.setJobTitle(job.getTitle());
        }

        // 关联查询候选人姓名
        Candidate candidate = candidateMapper.selectById(application.getCandidateId());
        if (candidate != null) {
            response.setCandidateName(candidate.getName());
        }

        return response;
    }

    /**
     * 分页结果转换
     */
    private Page<ApplicationResponse> convertPage(Page<JobApplication> sourcePage) {
        Page<ApplicationResponse> responsePage = new Page<>(
                sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());
        responsePage.setRecords(
                sourcePage.getRecords().stream()
                        .map(this::convertToResponse)
                        .toList()
        );
        return responsePage;
    }

    /**
     * 状态中文描述
     */
    private String getStatusDesc(String status) {
        if (status == null) return "";
        return switch (status) {
            case "PENDING"    -> "待处理";
            case "SCREENING"  -> "简历筛选中";
            case "INTERVIEW"  -> "面试中";
            case "OFFER"      -> "已发放 Offer";
            case "REJECTED"   -> "已淘汰";
            case "WITHDRAWN"  -> "已撤回";
            default -> status;
        };
    }

    /**
     * 清除申请详情缓存
     */
    private void evictCache(Long id) {
        redisTemplate.delete(RedisKeyConstants.CACHE_APPLICATION_KEY_PREFIX + id);
        log.debug("申请缓存已失效：id={}", id);
    }
}
