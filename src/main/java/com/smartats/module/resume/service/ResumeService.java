package com.smartats.module.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.resume.dto.ResumeUploadResponse;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.mapper.ResumeMapper;
import com.smartats.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeMapper resumeMapper;
    private final FileStorageService fileStorageService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RESUME_DEDUP_KEY_PREFIX = "dedup:resume:";
    private static final String TASK_STATUS_KEY_PREFIX = "task:resume:";
    private static final long TASK_STATUS_TTL = 24; // 24小时

    /**
     * 上传简历
     */
    @Transactional(rollbackFor = Exception.class)
    public ResumeUploadResponse uploadResume(MultipartFile file, Long userId) {
        // 1. 校验文件
        validateFile(file);

        // 2. 计算 MD5
        String fileHash;
        try {
            fileHash = DigestUtils.md5Hex(file.getInputStream());
        } catch (IOException e) {
            log.error("计算文件MD5失败", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件处理失败");
        }

        // 3. 检查去重（Redis + DB）
        Resume existingResume = checkDuplicate(fileHash);
        if (existingResume != null) {
            log.info("文件已存在: hash={}, userId={}", fileHash, userId);
            return new ResumeUploadResponse(
                    existingResume.getId().toString(),
                    existingResume.getId(),
                    true,
                    "文件已存在，直接使用已有简历"
            );
        }

        // 4. 生成文件路径
        String objectName = generateObjectName(fileHash, file.getOriginalFilename());

        // 5. 上传文件到 MinIO
        String fileUrl;
        try {
            fileUrl = fileStorageService.uploadFile(file, objectName);
        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败: objectName={}", objectName, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR);
        }

        // 6. 保存数据库记录
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(file.getOriginalFilename());
        resume.setFilePath(objectName);
        resume.setFileUrl(fileUrl);
        resume.setFileSize(file.getSize());
        resume.setFileHash(fileHash);
        resume.setFileType(file.getContentType());
        resume.setStatus("PARSING");
        resume.setCreatedAt(LocalDateTime.now());
        resume.setUpdatedAt(LocalDateTime.now());

        resumeMapper.insert(resume);

        // 7. 写入去重标记（Redis）
        String dedupKey = RESUME_DEDUP_KEY_PREFIX + fileHash;
        redisTemplate.opsForValue().set(dedupKey, resume.getId(), 7, TimeUnit.DAYS);

        // 8. 生成任务ID
        String taskId = UUID.randomUUID().toString();

        // 9. 写入任务状态（Redis）
        TaskStatusResponse taskStatus = new TaskStatusResponse();
        taskStatus.setStatus("QUEUED");
        taskStatus.setProgress(0);

        String taskKey = TASK_STATUS_KEY_PREFIX + taskId;
        redisTemplate.opsForValue().set(taskKey, taskStatus, TASK_STATUS_TTL, TimeUnit.HOURS);

        // 10. 发送 MQ 消息（下一阶段实现）
        // TODO: 发送 MQ 消息到解析队列

        log.info("简历上传成功: resumeId={}, taskId={}, hash={}", resume.getId(), taskId, fileHash);

        return new ResumeUploadResponse(
                taskId,
                resume.getId(),
                false,
                "简历上传成功，正在解析中"
        );
    }

    /**
     * 查询任务状态
     */
    public TaskStatusResponse getTaskStatus(String taskId) {
        String taskKey = TASK_STATUS_KEY_PREFIX + taskId;

        // 1. 先查 Redis
        TaskStatusResponse status = (TaskStatusResponse) redisTemplate.opsForValue().get(taskKey);

        if (status != null) {
            return status;
        }

        // 2. Redis 没有，返回默认状态
        return new TaskStatusResponse("NOT_FOUND", null, null, null, 0);
    }

    /**
     * 校验文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件不能为空");
        }

        // 校验文件大小（10MB）
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED);
        }

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("application/pdf") &&
                 !contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") &&
                 !contentType.equals("application/msword"))) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED);
        }
    }

    /**
     * 检查文件是否已存在
     */
    private Resume checkDuplicate(String fileHash) {
        // 1. 先查 Redis 去重标记
        String dedupKey = RESUME_DEDUP_KEY_PREFIX + fileHash;
        Object cachedResumeId = redisTemplate.opsForValue().get(dedupKey);

        if (cachedResumeId != null) {
            Long resumeId = Long.valueOf(cachedResumeId.toString());
            return resumeMapper.selectById(resumeId);
        }

        // 2. Redis 没有，查数据库
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resume::getFileHash, fileHash);
        wrapper.last("LIMIT 1");

        Resume resume = resumeMapper.selectOne(wrapper);

        // 3. 如果数据库有，回填 Redis
        if (resume != null) {
            redisTemplate.opsForValue().set(dedupKey, resume.getId(), 7, TimeUnit.DAYS);
        }

        return resume;
    }

    /**
     * 生成对象名（文件路径）
     * 格式：resumes/2026/02/19/{md5前8位}_{原文件名}
     */
    private String generateObjectName(String fileHash, String originalFilename) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String prefix = fileHash.substring(0, 8);
        return String.format("resumes/%s/%s_%s", date, prefix, originalFilename);
    }
}