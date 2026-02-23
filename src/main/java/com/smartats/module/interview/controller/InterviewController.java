package com.smartats.module.interview.controller;

import com.smartats.common.result.Result;
import com.smartats.module.interview.dto.InterviewResponse;
import com.smartats.module.interview.dto.ScheduleInterviewRequest;
import com.smartats.module.interview.dto.SubmitFeedbackRequest;
import com.smartats.module.interview.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 面试记录管理控制器
 * <p>
 * 提供面试安排、反馈提交、取消、查询等接口。
 * 统一通过 Authentication.getPrincipal() 获取当前登录用户 ID。
 */
@Slf4j
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * 安排面试
     * POST /api/v1/interviews
     */
    @PostMapping
    public Result<Long> scheduleInterview(
            @Valid @RequestBody ScheduleInterviewRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("安排面试：userId={}, applicationId={}", userId, request.getApplicationId());

        Long interviewId = interviewService.scheduleInterview(request);
        return Result.success(interviewId);
    }

    /**
     * 提交面试反馈
     * PUT /api/v1/interviews/{id}/feedback
     */
    @PutMapping("/{id}/feedback")
    public Result<Void> submitFeedback(
            @PathVariable Long id,
            @Valid @RequestBody SubmitFeedbackRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("提交面试反馈：userId={}, interviewId={}", userId, id);

        interviewService.submitFeedback(id, request);
        return Result.success();
    }

    /**
     * 取消面试
     * POST /api/v1/interviews/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancelInterview(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("取消面试：userId={}, interviewId={}", userId, id);

        interviewService.cancelInterview(id);
        return Result.success();
    }

    /**
     * 获取面试记录详情
     * GET /api/v1/interviews/{id}
     */
    @GetMapping("/{id}")
    public Result<InterviewResponse> getById(@PathVariable Long id) {
        InterviewResponse response = interviewService.getById(id);
        return Result.success(response);
    }

    /**
     * 按申请查询所有面试轮次
     * GET /api/v1/interviews/application/{applicationId}
     */
    @GetMapping("/application/{applicationId}")
    public Result<List<InterviewResponse>> listByApplicationId(@PathVariable Long applicationId) {
        List<InterviewResponse> interviews = interviewService.listByApplicationId(applicationId);
        return Result.success(interviews);
    }
}
