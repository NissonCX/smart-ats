package com.smartats.module.webhook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.webhook.entity.WebhookLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Webhook 日志 Mapper
 */
@Mapper
public interface WebhookLogMapper extends BaseMapper<WebhookLog> {
}
