package com.smartats.common.enums;

import lombok.Getter;

/**
 * 面试类型枚举
 */
@Getter
public enum InterviewType {

    PHONE("PHONE", "电话面试"),
    VIDEO("VIDEO", "视频面试"),
    ONSITE("ONSITE", "现场面试"),
    WRITTEN_TEST("WRITTEN_TEST", "笔试");

    private final String code;
    private final String description;

    InterviewType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static InterviewType fromCode(String code) {
        if (code == null) return null;
        for (InterviewType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    public static String getDescriptionByCode(String code) {
        InterviewType type = fromCode(code);
        return type != null ? type.description : code;
    }
}
