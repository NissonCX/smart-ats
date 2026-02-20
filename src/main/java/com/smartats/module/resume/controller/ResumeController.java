package com.smartats.module.resume.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.result.Result;
import com.smartats.module.resume.dto.ResumeUploadResponse;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /**
     * 上传简历
     *
     * @param file          简历文件
     * @param authentication Spring Security 认证信息（自动注入）
     * @return 上传结果，包含 taskId 用于查询解析状态
     */
    @PostMapping("/upload")
    public Result<ResumeUploadResponse> uploadResume(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        // 从 SecurityContext 中获取 userId（JWT 过滤器已解析）
        Long userId = (Long) authentication.getPrincipal();

        log.info("收到简历上传请求: userId={}, fileName={}, size={}",
                userId, file.getOriginalFilename(), file.getSize());

        ResumeUploadResponse response = resumeService.uploadResume(file, userId);

        return Result.success(response);
    }

    /**
     * 查询任务状态
     *
     * @param taskId 任务ID
     * @return 任务状态，包含解析进度、结果等
     */
    @GetMapping("/tasks/{taskId}")
    public Result<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        log.debug("查询任务状态: taskId={}", taskId);

        TaskStatusResponse response = resumeService.getTaskStatus(taskId);

        return Result.success(response);
    }

    /**
     * 获取简历详情
     *
     * @param id             简历ID
     * @param authentication 登录用户（仅能查看自己的简历）
     * @return 简历详情
     */
    @GetMapping("/{id}")
    public Result<Resume> getResumeById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        Resume resume = resumeService.getResumeById(id, userId);
        return Result.success(resume);
    }

    /**
     * 分页查询简历列表（当前用户的简历）
     *
     * @param page           页码（默认1）
     * @param size           每页条数（默认10）
     * @param authentication 登录用户
     * @return 简历分页列表
     */
    @GetMapping
    public Result<Page<Resume>> listResumes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        Page<Resume> result = resumeService.listResumes(userId, page, size);
        return Result.success(result);
    }
}