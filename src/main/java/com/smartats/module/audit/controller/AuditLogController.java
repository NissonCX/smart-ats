package com.smartats.module.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.result.Result;
import com.smartats.module.audit.dto.AuditLogQueryRequest;
import com.smartats.module.audit.dto.AuditLogResponse;
import com.smartats.module.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志管理接口
 * <p>
 * 提供审计日志的查询能力，仅管理员可访问。
 */
@Tag(name = "审计日志", description = "操作审计日志查询（AOP 自动采集）")
@Slf4j
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 分页查询审计日志
     * <p>
     * 支持按用户、模块、操作类型、时间范围、关键词筛选
     */
    @Operation(summary = "分页查询审计日志", description = "支持多维筛选：用户、模块、操作类型、时间范围、关键词")
    @GetMapping
    public Result<Page<AuditLogResponse>> queryAuditLogs(AuditLogQueryRequest request) {
        log.info("查询审计日志：module={}, operation={}, userId={}", request.getModule(),
                request.getOperation(), request.getUserId());
        Page<AuditLogResponse> page = auditLogService.queryAuditLogs(request);
        return Result.success(page);
    }
}
