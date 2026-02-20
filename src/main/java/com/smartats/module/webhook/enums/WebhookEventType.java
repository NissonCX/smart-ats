package com.smartats.module.webhook.enums;

import lombok.Getter;

/**
 * Webhook 事件类型
 */
@Getter
public enum WebhookEventType {

    /**
     * 简历相关事件
     */
    RESUME_UPLOADED("resume.uploaded", "新简历上传"),
    RESUME_PARSE_COMPLETED("resume.parse_completed", "简历解析完成"),
    RESUME_PARSE_FAILED("resume.parse_failed", "简历解析失败"),

    /**
     * 候选人相关事件
     */
    CANDIDATE_CREATED("candidate.created", "候选人信息创建"),
    CANDIDATE_UPDATED("candidate.updated", "候选人信息更新"),

    /**
     * 职位申请相关事件
     */
    APPLICATION_SUBMITTED("application.submitted", "提交职位申请"),
    APPLICATION_STATUS_CHANGED("application.status_changed", "申请状态变更"),

    /**
     * 面试相关事件
     */
    INTERVIEW_SCHEDULED("interview.scheduled", "面试安排"),
    INTERVIEW_COMPLETED("interview.completed", "面试完成"),
    INTERVIEW_CANCELLED("interview.cancelled", "面试取消"),

    /**
     * 系统事件
     */
    SYSTEM_ERROR("system.error", "系统错误"),
    SYSTEM_MAINTENANCE("system.maintenance", "系统维护通知");

    private final String code;
    private final String description;

    WebhookEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static WebhookEventType fromCode(String code) {
        for (WebhookEventType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
