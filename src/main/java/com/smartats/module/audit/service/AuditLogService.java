package com.smartats.module.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.module.audit.dto.AuditLogQueryRequest;
import com.smartats.module.audit.dto.AuditLogResponse;
import com.smartats.module.audit.entity.AuditLogEntity;
import com.smartats.module.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 审计日志服务
 * <p>
 * 提供异步写入和分页查询能力，确保审计日志的记录不影响主业务性能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 异步保存审计日志
     * <p>
     * 使用独立线程池写入，不阻塞主业务流程。
     * 即使写入失败也不影响业务（仅打印 ERROR 日志）。
     *
     * @param entity 审计日志实体
     */
    @Async("asyncExecutor")
    public void saveAsync(AuditLogEntity entity) {
        try {
            auditLogMapper.insert(entity);
            log.debug("审计日志写入成功: module={}, operation={}, userId={}",
                    entity.getModule(), entity.getOperation(), entity.getUserId());
        } catch (Exception e) {
            // 审计日志写入失败不应影响业务，仅记录错误
            log.error("审计日志写入失败: module={}, operation={}, error={}",
                    entity.getModule(), entity.getOperation(), e.getMessage(), e);
        }
    }

    /**
     * 分页查询审计日志（支持多维筛选）
     *
     * @param request 查询参数
     * @return 分页结果
     */
    public Page<AuditLogResponse> queryAuditLogs(AuditLogQueryRequest request) {
        int pageNum = Math.max(1, request.getPageNum());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize()));

        Page<AuditLogEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<>();

        // 筛选条件
        if (request.getUserId() != null) {
            wrapper.eq(AuditLogEntity::getUserId, request.getUserId());
        }
        if (StringUtils.hasText(request.getModule())) {
            wrapper.eq(AuditLogEntity::getModule, request.getModule());
        }
        if (StringUtils.hasText(request.getOperation())) {
            wrapper.eq(AuditLogEntity::getOperation, request.getOperation());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(AuditLogEntity::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.like(AuditLogEntity::getDescription, request.getKeyword());
        }

        // 时间范围
        if (StringUtils.hasText(request.getStartTime())) {
            wrapper.ge(AuditLogEntity::getCreatedAt, LocalDateTime.parse(request.getStartTime(), FORMATTER));
        }
        if (StringUtils.hasText(request.getEndTime())) {
            wrapper.le(AuditLogEntity::getCreatedAt, LocalDateTime.parse(request.getEndTime(), FORMATTER));
        }

        // 按时间倒序
        wrapper.orderByDesc(AuditLogEntity::getCreatedAt);

        Page<AuditLogEntity> resultPage = auditLogMapper.selectPage(page, wrapper);
        return convertPage(resultPage);
    }

    /**
     * 分页结果转换
     */
    private Page<AuditLogResponse> convertPage(Page<AuditLogEntity> sourcePage) {
        Page<AuditLogResponse> responsePage = new Page<>(
                sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());

        responsePage.setRecords(sourcePage.getRecords().stream()
                .map(this::convertToResponse)
                .toList());

        return responsePage;
    }

    /**
     * 实体 → 响应 DTO
     */
    private AuditLogResponse convertToResponse(AuditLogEntity entity) {
        AuditLogResponse response = new AuditLogResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setUsername(entity.getUsername());
        response.setModule(entity.getModule());
        response.setOperation(entity.getOperation());
        response.setDescription(entity.getDescription());
        response.setMethod(entity.getMethod());
        response.setRequestUrl(entity.getRequestUrl());
        response.setRequestMethod(entity.getRequestMethod());
        response.setRequestParams(entity.getRequestParams());
        response.setRequestIp(entity.getRequestIp());
        response.setStatus(entity.getStatus());
        response.setErrorMessage(entity.getErrorMessage());
        response.setDuration(entity.getDuration());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
