package com.smartats.module.application.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.result.Result;
import com.smartats.module.application.dto.*;
import com.smartats.module.application.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 职位申请管理控制器
 * <p>
 * 提供创建申请、状态变更、多维查询等接口。
 * 统一通过 Authentication.getPrincipal() 获取当前登录用户 ID。
 */
@Slf4j
@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    /**
     * 创建职位申请
     * POST /api/v1/applications
     */
    @PostMapping
    public Result<Long> createApplication(
            @Valid @RequestBody CreateApplicationRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("创建职位申请：userId={}, jobId={}, candidateId={}",
                userId, request.getJobId(), request.getCandidateId());

        Long applicationId = jobApplicationService.createApplication(request);
        return Result.success(applicationId);
    }

    /**
     * 更新申请状态
     * PUT /api/v1/applications/{id}/status
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationStatusRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("更新申请状态：userId={}, applicationId={}, targetStatus={}",
                userId, id, request.getStatus());

        jobApplicationService.updateStatus(id, request);
        return Result.success();
    }

    /**
     * 获取申请详情
     * GET /api/v1/applications/{id}
     */
    @GetMapping("/{id}")
    public Result<ApplicationResponse> getById(@PathVariable Long id) {
        ApplicationResponse response = jobApplicationService.getById(id);
        return Result.success(response);
    }

    /**
     * 按职位查询申请列表（HR 视角）
     * GET /api/v1/applications/job/{jobId}
     */
    @GetMapping("/job/{jobId}")
    public Result<Page<ApplicationResponse>> listByJobId(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ApplicationResponse> page = jobApplicationService.listByJobId(jobId, pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * 按候选人查询申请列表
     * GET /api/v1/applications/candidate/{candidateId}
     */
    @GetMapping("/candidate/{candidateId}")
    public Result<Page<ApplicationResponse>> listByCandidateId(
            @PathVariable Long candidateId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ApplicationResponse> page = jobApplicationService.listByCandidateId(candidateId, pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * 综合查询申请列表（支持多维筛选 + 分页 + 排序）
     * GET /api/v1/applications
     */
    @GetMapping
    public Result<Page<ApplicationResponse>> listApplications(ApplicationQueryRequest request) {
        Page<ApplicationResponse> page = jobApplicationService.listApplications(request);
        return Result.success(page);
    }
}
