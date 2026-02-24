package com.smartats.module.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.enums.ResumeStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.common.util.FileValidationUtil;
import com.smartats.infrastructure.mq.MessagePublisher;
import com.smartats.module.resume.dto.BatchUploadResponse;
import com.smartats.module.resume.dto.BatchUploadResponse.BatchUploadItem;
import com.smartats.module.resume.dto.ResumeParseMessage;
import com.smartats.module.resume.dto.ResumeUploadResponse;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.mapper.ResumeMapper;
import com.smartats.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 简历服务
 * <p>
 * 功能：
 * 1. 简历上传（文件存储 + 去重检查）
 * 2. 任务状态查询
 * 3. 异步解析（MQ 消费者已在 ResumeParseConsumer 中实现）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeMapper resumeMapper;
    private final FileStorageService fileStorageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MessagePublisher messagePublisher;

    private static final String RESUME_DEDUP_KEY_PREFIX = RedisKeyConstants.RESUME_DEDUP_KEY_PREFIX;
    private static final String TASK_STATUS_KEY_PREFIX = RedisKeyConstants.RESUME_TASK_KEY_PREFIX;
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
            // taskId 为 null：重复文件无需发起解析任务，客户端无需轮询状态
            return new ResumeUploadResponse(null, existingResume.getId(), true, "文件已存在，直接使用已有简历");
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
        // 🔒 安全：使用消毒后的文件名
        resume.setFileName(FileValidationUtil.sanitizeFilename(file.getOriginalFilename()));
        resume.setFilePath(objectName);
        resume.setFileUrl(fileUrl);
        resume.setFileSize(file.getSize());
        resume.setFileHash(fileHash);
        resume.setFileType(file.getContentType());
        resume.setStatus(ResumeStatus.PARSING.getCode());
        resume.setCreatedAt(LocalDateTime.now());
        resume.setUpdatedAt(LocalDateTime.now());

        resumeMapper.insert(resume);

        // 7. 写入去重标记（Redis）
        String dedupKey = RESUME_DEDUP_KEY_PREFIX + fileHash;
        stringRedisTemplate.opsForValue().set(dedupKey, resume.getId().toString(), 7, TimeUnit.DAYS);

        // 8. 生成任务ID
        String taskId = UUID.randomUUID().toString();

        // 9. 写入任务状态（Redis）
        TaskStatusResponse taskStatus = new TaskStatusResponse();
        taskStatus.setStatus("QUEUED");
        taskStatus.setProgress(0);

        String taskKey = TASK_STATUS_KEY_PREFIX + taskId;
        try {
            String json = objectMapper.writeValueAsString(taskStatus);
            stringRedisTemplate.opsForValue().set(taskKey, json, TASK_STATUS_TTL, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("任务状态序列化失败: taskKey={}", taskKey, e);
        }

        // 10. 发送 MQ 消息
        try {
            ResumeParseMessage message = new ResumeParseMessage(taskId, resume.getId(), userId, fileHash, 0);

            messagePublisher.sendResumeParseMessage(message);

            log.info("发送解析消息成功: taskId={}, resumeId={}", taskId, resume.getId());

        } catch (Exception e) {
            log.error("发送解析消息失败: taskId={}", taskId, e);
            // MQ 发送失败时更新任务状态为 FAILED，避免用户永久看到 QUEUED
            try {
                TaskStatusResponse failedStatus = new TaskStatusResponse();
                failedStatus.setStatus(ResumeStatus.FAILED.getCode());
                failedStatus.setProgress(0);
                failedStatus.setErrorMessage("消息队列发送失败，请稍后重试");
                stringRedisTemplate.opsForValue().set(taskKey,
                        objectMapper.writeValueAsString(failedStatus), TASK_STATUS_TTL, TimeUnit.HOURS);
            } catch (Exception ex) {
                log.error("更新失败状态异常: taskId={}", taskId, ex);
            }
        }

        log.info("简历上传成功: resumeId={}, taskId={}, hash={}", resume.getId(), taskId, fileHash);

        return new ResumeUploadResponse(taskId, resume.getId(), false, "简历上传成功，正在解析中");
    }

    /**
     * 查询任务状态
     */
    public TaskStatusResponse getTaskStatus(String taskId) {
        String taskKey = TASK_STATUS_KEY_PREFIX + taskId;

        log.debug("查询任务状态: taskKey={}", taskKey);

        // 1. 先查 Redis
        String json = stringRedisTemplate.opsForValue().get(taskKey);

        if (json != null) {
            try {
                TaskStatusResponse status = objectMapper.readValue(json, TaskStatusResponse.class);
                log.debug("任务状态查询成功: taskId={}, status={}", taskId, status.getStatus());
                return status;
            } catch (Exception e) {
                log.error("任务状态反序列化失败: taskKey={}, json={}", taskKey, json, e);
            }
        }

        // 2. Redis 没有，返回默认状态
        log.debug("任务状态不存在: taskId={}", taskId);
        return new TaskStatusResponse("NOT_FOUND", null, null, null, 0, null, null);
    }

    /**
     * 根据ID查询简历详情（仅限该用户自己的简历）
     */
    public Resume getResumeById(Long id, Long userId) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new BusinessException(ResultCode.RESUME_NOT_FOUND);
        }
        // 安全校验：只能查看自己的简历
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该简历");
        }
        return resume;
    }

    /**
     * 分页查询当前用户的简历列表
     */
    public Page<Resume> listResumes(Long userId, int pageNum, int pageSize) {
        Page<Resume> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resume::getUserId, userId)
               .orderByDesc(Resume::getCreatedAt);
        return resumeMapper.selectPage(page, wrapper);
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

        // 校验文件类型（通过 Content-Type）
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/pdf")
                && !contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                && !contentType.equals("application/msword"))) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED);
        }

        // 🔒 安全增强：通过文件头（魔数）验证真实文件类型
        // 使用 getBytes() 而非 getInputStream() 避免流 mark/reset 不支持的问题
        try {
            byte[] fileBytes = file.getBytes();
            boolean isValid = FileValidationUtil.validateFileType(
                    fileBytes,
                    contentType,
                    file.getOriginalFilename()
            );

            if (!isValid) {
                log.warn("文件类型验证失败: filename={}, contentType={}", file.getOriginalFilename(), contentType);
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED, "文件内容与声明的类型不匹配");
            }
        } catch (IOException e) {
            log.error("读取文件内容失败", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件验证失败");
        }
    }

    /**
     * 检查文件是否已存在
     */
    private Resume checkDuplicate(String fileHash) {
        // 1. 先查 Redis 去重标记
        String dedupKey = RESUME_DEDUP_KEY_PREFIX + fileHash;
        String cachedResumeId = stringRedisTemplate.opsForValue().get(dedupKey);

        if (cachedResumeId != null) {
            Long resumeId = Long.valueOf(cachedResumeId);
            return resumeMapper.selectById(resumeId);
        }

        // 2. Redis 没有，查数据库
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resume::getFileHash, fileHash);
        wrapper.last("LIMIT 1");

        Resume resume = resumeMapper.selectOne(wrapper);

        // 3. 如果数据库有，回填 Redis
        if (resume != null) {
            stringRedisTemplate.opsForValue().set(dedupKey, resume.getId().toString(), 7, TimeUnit.DAYS);
        }

        return resume;
    }

    /**
     * 生成对象名（文件路径）
     * 格式：resumes/2026/02/19/{md5前8位}_{清理后文件名}
     * 🔒 安全：使用消毒后的文件名防止路径穿越攻击
     */
    private String generateObjectName(String fileHash, String originalFilename) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String prefix = fileHash.substring(0, 8);
        // 🔒 对文件名进行清理，防止路径穿越（如 ../../etc/passwd）
        String safeFilename = FileValidationUtil.sanitizeFilename(originalFilename);
        return String.format("resumes/%s/%s_%s", date, prefix, safeFilename);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 批量上传
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private static final int MAX_BATCH_SIZE = 20;
    private static final int MAX_BATCH_UPLOADS_PER_MINUTE = 5;

    /**
     * 批量上传简历
     * <p>
     * 限制：最多 20 个文件，每分钟最多 5 次批量上传
     * 每个文件独立处理，单个失败不影响其他文件
     */
    public BatchUploadResponse batchUploadResumes(MultipartFile[] files, Long userId) {
        if (files == null || files.length == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        if (files.length > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.BATCH_UPLOAD_LIMIT_EXCEEDED);
        }

        // 频率限流
        checkBatchUploadRateLimit(userId);

        List<BatchUploadItem> items = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
            try {
                ResumeUploadResponse result = uploadResume(file, userId);
                if (Boolean.TRUE.equals(result.getDuplicated())) {
                    items.add(new BatchUploadItem(null, result.getResumeId(), fileName, "DUPLICATE", result.getMessage()));
                } else {
                    items.add(new BatchUploadItem(result.getTaskId(), result.getResumeId(), fileName, "QUEUED", result.getMessage()));
                }
                successCount++;
            } catch (BusinessException e) {
                log.warn("批量上传单文件失败: fileName={}, error={}", fileName, e.getMessage());
                items.add(new BatchUploadItem(null, null, fileName, "FAILED", e.getMessage()));
                failedCount++;
            } catch (Exception e) {
                log.error("批量上传单文件异常: fileName={}", fileName, e);
                items.add(new BatchUploadItem(null, null, fileName, "FAILED", "处理失败"));
                failedCount++;
            }
        }

        log.info("批量上传完成: userId={}, total={}, success={}, failed={}",
                userId, files.length, successCount, failedCount);

        return new BatchUploadResponse(files.length, successCount, failedCount, items);
    }

    /**
     * 批量上传频率限制：每分钟最多 5 次
     */
    private void checkBatchUploadRateLimit(Long userId) {
        String rateLimitKey = RedisKeyConstants.UPLOAD_RATE_LIMIT_KEY_PREFIX + userId;
        String countStr = stringRedisTemplate.opsForValue().get(rateLimitKey);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        if (count >= MAX_BATCH_UPLOADS_PER_MINUTE) {
            throw new BusinessException(ResultCode.UPLOAD_RATE_LIMITED);
        }
        stringRedisTemplate.opsForValue().increment(rateLimitKey);
        if (count == 0) {
            stringRedisTemplate.expire(rateLimitKey, 60, TimeUnit.SECONDS);
        }
    }
}
