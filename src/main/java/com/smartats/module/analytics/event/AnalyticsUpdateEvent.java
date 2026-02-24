package com.smartats.module.analytics.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 分析数据更新事件
 * <p>
 * 当招聘管线发生变化（申请创建/状态流转/面试安排等）时发布此事件，
 * SSE 推送管理器监听该事件并将更新推送到所有已连接的 Dashboard 客户端。
 * <p>
 * 使用 Spring ApplicationEvent + EventListener 实现松耦合：
 * 业务服务只负责发布事件，不直接依赖 SSE 组件。
 */
@Getter
public class AnalyticsUpdateEvent extends ApplicationEvent {

    /**
     * 事件类型（如 APPLICATION_CREATED, STATUS_CHANGED, INTERVIEW_SCHEDULED）
     */
    private final String eventType;

    /**
     * 事件描述（简短文字）
     */
    private final String message;

    public AnalyticsUpdateEvent(Object source, String eventType, String message) {
        super(source);
        this.eventType = eventType;
        this.message = message;
    }
}
