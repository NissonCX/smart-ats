package com.smartats.module.application.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.result.Result;
import com.smartats.module.application.dto.*;
import com.smartats.module.application.service.JobApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "职位申请管理", description = "申请创建、状态流转(PENDING→REVIEWING→INTERVIEW→OFFER/REJECTED)、多维查询")
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
    @Operation(summary = "创建职位申请", description = "自动去重（同一候选人不能重复申请同一职位）")
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
    @Operation(summary = "更新申请状态", description = "状态流转校验，触发 Webhook 通知")
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
    @Operation(summary = "获取申请详情")
    @GetMapping("/{id}")
    public Result<ApplicationResponse> getById(@PathVariable Long id) {
        ApplicationResponse response = jobApplicationService.getById(id);
        return Result.success(response);
    }

    /**
     * 按职位查询申请列表（HR 视角）
     * GET /api/v1/applications/job/{jobId}
     */
    @Operation(summary = "按职位查询申请列表")
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
    @Operation(summary = "按候选人查询申请列表")
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
    @Operation(summary = "综合查询申请列表", description = "支持多维筛选 + 分页 + 排序")
    @GetMapping
    public Result<Page<ApplicationResponse>> listApplications(ApplicationQueryRequest request) {
        Page<ApplicationResponse> page = jobApplicationService.listApplications(request);
        return Result.success(page);
    }
}
