package com.smartats.common.enums;

import lombok.Getter;

/**
 * 面试状态枚举
 */
@Getter
public enum InterviewStatus {

    SCHEDULED("SCHEDULED", "已安排"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消"),
    NO_SHOW("NO_SHOW", "未出席");

    private final String code;
    private final String description;

    InterviewStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static InterviewStatus fromCode(String code) {
        if (code == null) return null;
        for (InterviewStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDescriptionByCode(String code) {
        InterviewStatus status = fromCode(code);
        return status != null ? status.description : code;
    }
}
