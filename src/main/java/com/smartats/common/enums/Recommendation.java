package com.smartats.common.enums;

import lombok.Getter;

/**
 * 面试推荐结论枚举
 */
@Getter
public enum Recommendation {

    STRONG_YES("STRONG_YES", "强烈推荐"),
    YES("YES", "推荐"),
    NEUTRAL("NEUTRAL", "中性"),
    NO("NO", "不推荐"),
    STRONG_NO("STRONG_NO", "强烈不推荐");

    private final String code;
    private final String description;

    Recommendation(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static Recommendation fromCode(String code) {
        if (code == null) return null;
        for (Recommendation r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        return null;
    }

    public static String getDescriptionByCode(String code) {
        Recommendation r = fromCode(code);
        return r != null ? r.description : code;
    }
}
