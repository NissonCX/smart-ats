package com.smartats.common.enums;

import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 职位申请状态枚举
 * <p>
 * 包含合法的状态流转规则（状态机）
 */
@Getter
public enum ApplicationStatus {

    PENDING("PENDING", "待处理"),
    SCREENING("SCREENING", "简历筛选中"),
    INTERVIEW("INTERVIEW", "面试中"),
    OFFER("OFFER", "已发放 Offer"),
    REJECTED("REJECTED", "已淘汰"),
    WITHDRAWN("WITHDRAWN", "已撤回");

    private final String code;
    private final String description;

    ApplicationStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 合法的状态流转映射
     * <p>
     * Key: 当前状态, Value: 允许跳转的目标状态集合
     */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> VALID_TRANSITIONS = Map.of(
            PENDING,   Set.of(SCREENING, REJECTED, WITHDRAWN),
            SCREENING, Set.of(INTERVIEW, REJECTED, WITHDRAWN),
            INTERVIEW, Set.of(OFFER, REJECTED, WITHDRAWN),
            OFFER,     Set.of(WITHDRAWN),
            REJECTED,  Set.of(),
            WITHDRAWN, Set.of()
    );

    /**
     * 判断是否允许从当前状态流转到目标状态
     */
    public boolean canTransitionTo(ApplicationStatus target) {
        Set<ApplicationStatus> allowed = VALID_TRANSITIONS.getOrDefault(this, Set.of());
        return allowed.contains(target);
    }

    /**
     * 获取允许的目标状态集合
     */
    public Set<ApplicationStatus> getAllowedTargets() {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of());
    }

    /**
     * 根据 code 获取枚举
     */
    public static ApplicationStatus fromCode(String code) {
        if (code == null) return null;
        for (ApplicationStatus status : values()) {
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
        ApplicationStatus status = fromCode(code);
        return status != null ? status.description : code;
    }
}
