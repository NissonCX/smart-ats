package com.smartats.module.audit.dto;

import lombok.Data;

/**
 * 审计日志查询请求 DTO
 */
@Data
public class AuditLogQueryRequest {

    /** 操作人用户 ID */
    private Long userId;

    /** 业务模块（精确匹配） */
    private String module;

    /** 操作类型（精确匹配） */
    private String operation;

    /** 操作结果：SUCCESS / FAILED */
    private String status;

    /** 关键词搜索（匹配 description） */
    private String keyword;

    /** 开始时间（格式：yyyy-MM-dd HH:mm:ss） */
    private String startTime;

    /** 结束时间（格式：yyyy-MM-dd HH:mm:ss） */
    private String endTime;

    /** 页码（默认 1） */
    private Integer pageNum = 1;

    /** 每页大小（默认 20） */
    private Integer pageSize = 20;
}
