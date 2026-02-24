package com.smartats.common.enums;

import lombok.Getter;

/**
 * 职位状态枚举
 */
@Getter
public enum JobStatus {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String description;

    JobStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     */
    public static JobStatus fromCode(String code) {
        if (code == null) return null;
        for (JobStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 获取中文描述，找不到则返回原始值
     */
    public static String getDescriptionByCode(String code) {
        JobStatus status = fromCode(code);
        return status != null ? status.description : code;
    }
}
