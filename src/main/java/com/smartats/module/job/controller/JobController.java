package com.smartats.module.job.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.annotation.AuditLog;
import com.smartats.common.result.Result;
import com.smartats.module.job.dto.request.CreateJobRequest;
import com.smartats.module.job.dto.request.JobQueryRequest;
import com.smartats.module.job.dto.request.UpdateJobRequest;
import com.smartats.module.job.dto.response.JobResponse;
import com.smartats.module.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 职位管理控制器
 */
@Tag(name = "职位管理", description = "职位 CRUD、发布/关闭、热门排行、浏览计数")
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /**
     * 创建职位
     * POST /api/v1/jobs
     */
    @Operation(summary = "创建职位")
    @AuditLog(module = "职位管理", operation = "CREATE", description = "创建职位")
    @PostMapping
    public Result<Long> createJob(@Valid @RequestBody CreateJobRequest request, Authentication authentication) {
        Long creatorId = (Long) authentication.getPrincipal();
        Long jobId = jobService.createJob(request, creatorId);
        return Result.success(jobId);
    }

    /**
     * 更新职位
     * PUT /api/v1/jobs
     */
    @Operation(summary = "更新职位")
    @AuditLog(module = "职位管理", operation = "UPDATE", description = "更新职位")
    @PutMapping
    public Result<Void> updateJob(@Valid @RequestBody UpdateJobRequest request, Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        jobService.updateJob(request, operatorId);
        return Result.success();
    }

    /**
     * 获取职位详情
     * GET /api/v1/jobs/{id}
     */
    @Operation(summary = "获取职位详情", description = "Redis 缓存优先，30分钟 TTL")
    @GetMapping("/{id}")
    public Result<JobResponse> getJobDetail(@PathVariable Long id) {
        JobResponse response = jobService.getJobDetail(id);
        return Result.success(response);
    }

    /**
     * 职位列表
     * GET /api/v1/jobs
     */
    @Operation(summary = "职位列表", description = "支持分页 + 多维度筛选")
    @GetMapping
    public Result<Page<JobResponse>> getJobList(JobQueryRequest request) {
        Page<JobResponse> page = jobService.getJobList(request);
        return Result.success(page);
    }

    /**
     * 发布职位
     * POST /api/v1/jobs/{id}/publish
     */
    @Operation(summary = "发布职位")
    @AuditLog(module = "职位管理", operation = "PUBLISH", description = "发布职位")
    @PostMapping("/{id}/publish")
    public Result<Void> publishJob(@PathVariable Long id, Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        jobService.publishJob(id, operatorId);
        return Result.success();
    }

    /**
     * 关闭职位
     * POST /api/v1/jobs/{id}/close
     */
    @Operation(summary = "关闭职位")
    @AuditLog(module = "职位管理", operation = "CLOSE", description = "关闭职位")
    @PostMapping("/{id}/close")
    public Result<Void> closeJob(@PathVariable Long id, Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        jobService.closeJob(id, operatorId);
        return Result.success();
    }

    /**
     * 删除职位
     * DELETE /api/v1/jobs/{id}
     */
    @Operation(summary = "删除职位")
    @AuditLog(module = "职位管理", operation = "DELETE", description = "删除职位")
    @DeleteMapping("/{id}")
    public Result<Void> deleteJob(@PathVariable Long id, Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        jobService.deleteJob(id, operatorId);
        return Result.success();
    }

    /**
     * 热门职位
     * GET /api/v1/jobs/hot
     */
    @Operation(summary = "热门职位", description = "Redis ZSet 排行榜，10分钟 TTL")
    @GetMapping("/hot")
    public Result<List<JobResponse>> getHotJobs(@Parameter(description = "返回数量") @RequestParam(defaultValue = "10") Integer limit) {
        List<JobResponse> jobs = jobService.getHotJobs(limit);
        return Result.success(jobs);
    }
}