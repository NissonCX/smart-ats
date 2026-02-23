package com.smartats.module.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.application.entity.JobApplication;
import com.smartats.module.application.mapper.JobApplicationMapper;
import com.smartats.module.auth.entity.User;
import com.smartats.module.auth.mapper.UserMapper;
import com.smartats.module.interview.dto.InterviewResponse;
import com.smartats.module.interview.dto.ScheduleInterviewRequest;
import com.smartats.module.interview.dto.SubmitFeedbackRequest;
import com.smartats.module.interview.entity.InterviewRecord;
import com.smartats.module.interview.mapper.InterviewRecordMapper;
import com.smartats.module.webhook.enums.WebhookEventType;
import com.smartats.module.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 面试记录服务
 * <p>
 * 核心业务逻辑：
 * <ul>
 *   <li>安排面试（自动递增轮次、关联申请校验、时间冲突检测）</li>
 *   <li>提交反馈（评分 + 评语 + 推荐结论）</li>
 *   <li>取消面试</li>
 *   <li>按申请查询所有面试轮次</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRecordMapper interviewRecordMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final UserMapper userMapper;
    private final WebhookService webhookService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 缓存 TTL（分钟） */
    private static final long CACHE_TTL_MINUTES = 30;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 安排面试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 安排面试
     *
     * @param request 面试安排请求
     * @return 新建面试记录的 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long scheduleInterview(ScheduleInterviewRequest request) {
        Long applicationId = request.getApplicationId();
        log.info("安排面试：applicationId={}, interviewerId={}, type={}",
                applicationId, request.getInterviewerId(), request.getInterviewType());

        // ① 校验申请是否存在且处于面试阶段
        JobApplication application = jobApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ResultCode.APPLICATION_NOT_FOUND);
        }
        // 只有 SCREENING 或 INTERVIEW 状态的申请才允许安排面试
        if (!"SCREENING".equals(application.getStatus()) && !"INTERVIEW".equals(application.getStatus())) {
            throw new BusinessException(ResultCode.APPLICATION_STATUS_INVALID,
                    "当前申请状态为 [" + application.getStatus() + "]，不允许安排面试");
        }

        // ② 校验面试官是否存在
        User interviewer = userMapper.selectById(request.getInterviewerId());
        if (interviewer == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "面试官不存在");
        }

        // ③ 自动计算面试轮次（如果没有手动指定）
        Integer round = request.getRound();
        if (round == null) {
            LambdaQueryWrapper<InterviewRecord> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(InterviewRecord::getApplicationId, applicationId);
            long existingRounds = interviewRecordMapper.selectCount(countWrapper);
            round = (int) existingRounds + 1;
        }

        // ④ 面试官时间冲突检测（同一面试官 ±面试时长 范围内不能有其他面试）
        int duration = request.getDurationMinutes() != null ? request.getDurationMinutes() : 60;
        LocalDateTime startTime = request.getScheduledAt();
        LocalDateTime endTime = startTime.plusMinutes(duration);

        LambdaQueryWrapper<InterviewRecord> conflictWrapper = new LambdaQueryWrapper<>();
        conflictWrapper.eq(InterviewRecord::getInterviewerId, request.getInterviewerId())
                .eq(InterviewRecord::getStatus, "SCHEDULED")
                .and(w -> w
                        // 已有面试的开始时间在新面试时间范围内
                        .between(InterviewRecord::getScheduledAt, startTime, endTime)
                        // 或已有面试包含新面试的开始时间
                        .or()
                        .le(InterviewRecord::getScheduledAt, startTime)
                        .apply("DATE_ADD(scheduled_at, INTERVAL duration_minutes MINUTE) > {0}", startTime)
                );

        if (interviewRecordMapper.selectCount(conflictWrapper) > 0) {
            throw new BusinessException(ResultCode.INTERVIEW_TIME_CONFLICT);
        }

        // ⑤ 创建面试记录
        InterviewRecord record = new InterviewRecord();
        record.setApplicationId(applicationId);
        record.setInterviewerId(request.getInterviewerId());
        record.setRound(round);
        record.setInterviewType(request.getInterviewType());
        record.setScheduledAt(request.getScheduledAt());
        record.setDurationMinutes(duration);
        record.setStatus("SCHEDULED");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        interviewRecordMapper.insert(record);

        // ⑥ 如果申请状态为 SCREENING，自动推进到 INTERVIEW
        if ("SCREENING".equals(application.getStatus())) {
            application.setStatus("INTERVIEW");
            application.setUpdatedAt(LocalDateTime.now());
            jobApplicationMapper.updateById(application);
            log.info("申请状态自动推进为 INTERVIEW：applicationId={}", applicationId);
        }

        log.info("面试安排成功：id={}, applicationId={}, round={}", record.getId(), applicationId, round);

        // ⑦ 触发 Webhook 事件
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("interviewId", record.getId());
        eventData.put("applicationId", applicationId);
        eventData.put("interviewerId", request.getInterviewerId());
        eventData.put("interviewerName", interviewer.getUsername());
        eventData.put("round", round);
        eventData.put("interviewType", request.getInterviewType());
        eventData.put("scheduledAt", request.getScheduledAt().toString());
        webhookService.sendEvent(WebhookEventType.INTERVIEW_SCHEDULED, eventData);

        return record.getId();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 提交反馈
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 提交面试反馈
     *
     * @param id      面试记录 ID
     * @param request 反馈内容
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitFeedback(Long id, SubmitFeedbackRequest request) {
        log.info("提交面试反馈：id={}, score={}, recommendation={}", id, request.getScore(), request.getRecommendation());

        InterviewRecord record = interviewRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.INTERVIEW_NOT_FOUND);
        }

        if ("CANCELLED".equals(record.getStatus())) {
            throw new BusinessException(ResultCode.INTERVIEW_ALREADY_CANCELLED);
        }

        // 填写反馈并标记完成
        record.setScore(request.getScore());
        record.setFeedback(request.getFeedback());
        record.setRecommendation(request.getRecommendation());
        record.setStatus("COMPLETED");
        record.setUpdatedAt(LocalDateTime.now());

        interviewRecordMapper.updateById(record);
        evictCache(id);

        log.info("面试反馈提交成功：id={}", id);

        // 触发 Webhook 事件
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("interviewId", id);
        eventData.put("applicationId", record.getApplicationId());
        eventData.put("round", record.getRound());
        eventData.put("score", request.getScore());
        eventData.put("recommendation", request.getRecommendation());
        webhookService.sendEvent(WebhookEventType.INTERVIEW_COMPLETED, eventData);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 取消面试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 取消面试
     *
     * @param id 面试记录 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelInterview(Long id) {
        log.info("取消面试：id={}", id);

        InterviewRecord record = interviewRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.INTERVIEW_NOT_FOUND);
        }

        if ("COMPLETED".equals(record.getStatus())) {
            throw new BusinessException(ResultCode.INTERVIEW_ALREADY_COMPLETED);
        }

        if ("CANCELLED".equals(record.getStatus())) {
            throw new BusinessException(ResultCode.INTERVIEW_ALREADY_CANCELLED);
        }

        record.setStatus("CANCELLED");
        record.setUpdatedAt(LocalDateTime.now());
        interviewRecordMapper.updateById(record);
        evictCache(id);

        log.info("面试已取消：id={}", id);

        // 触发 Webhook 事件
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("interviewId", id);
        eventData.put("applicationId", record.getApplicationId());
        eventData.put("round", record.getRound());
        webhookService.sendEvent(WebhookEventType.INTERVIEW_CANCELLED, eventData);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 查询
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 获取面试记录详情（Redis 缓存）
     */
    public InterviewResponse getById(Long id) {
        log.info("查询面试详情：id={}", id);

        String cacheKey = RedisKeyConstants.CACHE_INTERVIEW_KEY_PREFIX + id;

        // 1. 查缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, InterviewResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("面试缓存反序列化失败，降级查库：id={}", id, e);
            }
        }

        // 2. 查数据库
        InterviewRecord record = interviewRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.INTERVIEW_NOT_FOUND);
        }

        InterviewResponse response = convertToResponse(record);

        // 3. 回填缓存
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(response),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            log.warn("面试写入缓存失败（不影响业务）：id={}", id, e);
        }

        return response;
    }

    /**
     * 按申请 ID 查询所有面试轮次（批量查询面试官避免 N+1）
     */
    public List<InterviewResponse> listByApplicationId(Long applicationId) {
        log.info("查询申请的面试记录：applicationId={}", applicationId);

        LambdaQueryWrapper<InterviewRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewRecord::getApplicationId, applicationId)
                .orderByAsc(InterviewRecord::getRound);

        List<InterviewRecord> records = interviewRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return List.of();
        }

        // 批量查询所有面试官（避免 N+1）
        Set<Long> interviewerIds = records.stream()
                .map(InterviewRecord::getInterviewerId)
                .collect(Collectors.toSet());
        Map<Long, User> interviewerMap = userMapper.selectBatchIds(interviewerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return records.stream()
                .map(record -> convertToResponseWithMap(record, interviewerMap))
                .toList();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 私有方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 将实体转换为响应 DTO（单条查询使用，关联查询面试官）
     */
    private InterviewResponse convertToResponse(InterviewRecord record) {
        InterviewResponse response = buildBaseResponse(record);

        // 关联查询面试官姓名
        User interviewer = userMapper.selectById(record.getInterviewerId());
        if (interviewer != null) {
            response.setInterviewerName(interviewer.getUsername());
        }

        return response;
    }

    /**
     * 将实体转换为响应 DTO（使用预加载的面试官 Map，批量查询使用）
     */
    private InterviewResponse convertToResponseWithMap(InterviewRecord record, Map<Long, User> interviewerMap) {
        InterviewResponse response = buildBaseResponse(record);

        User interviewer = interviewerMap.get(record.getInterviewerId());
        if (interviewer != null) {
            response.setInterviewerName(interviewer.getUsername());
        }

        return response;
    }

    /**
     * 构建基础响应对象（不含关联查询，复用于单条和批量转换）
     */
    private InterviewResponse buildBaseResponse(InterviewRecord record) {
        InterviewResponse response = new InterviewResponse();
        response.setId(record.getId());
        response.setApplicationId(record.getApplicationId());
        response.setInterviewerId(record.getInterviewerId());
        response.setRound(record.getRound());
        response.setInterviewType(record.getInterviewType());
        response.setInterviewTypeDesc(getInterviewTypeDesc(record.getInterviewType()));
        response.setScheduledAt(record.getScheduledAt());
        response.setDurationMinutes(record.getDurationMinutes());
        response.setStatus(record.getStatus());
        response.setStatusDesc(getStatusDesc(record.getStatus()));
        response.setFeedback(record.getFeedback());
        response.setScore(record.getScore());
        response.setRecommendation(record.getRecommendation());
        response.setRecommendationDesc(getRecommendationDesc(record.getRecommendation()));
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    /**
     * 面试类型中文描述
     */
    private String getInterviewTypeDesc(String type) {
        if (type == null) return "";
        return switch (type) {
            case "PHONE"        -> "电话面试";
            case "VIDEO"        -> "视频面试";
            case "ONSITE"       -> "现场面试";
            case "WRITTEN_TEST" -> "笔试";
            default -> type;
        };
    }

    /**
     * 面试状态中文描述
     */
    private String getStatusDesc(String status) {
        if (status == null) return "";
        return switch (status) {
            case "SCHEDULED" -> "已安排";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            case "NO_SHOW"   -> "未出席";
            default -> status;
        };
    }

    /**
     * 推荐结论中文描述
     */
    private String getRecommendationDesc(String recommendation) {
        if (recommendation == null) return "";
        return switch (recommendation) {
            case "STRONG_YES" -> "强烈推荐";
            case "YES"        -> "推荐";
            case "NEUTRAL"    -> "中性";
            case "NO"         -> "不推荐";
            case "STRONG_NO"  -> "强烈不推荐";
            default -> recommendation;
        };
    }

    /**
     * 清除缓存
     */
    private void evictCache(Long id) {
        redisTemplate.delete(RedisKeyConstants.CACHE_INTERVIEW_KEY_PREFIX + id);
        log.debug("面试缓存已失效：id={}", id);
    }
}
