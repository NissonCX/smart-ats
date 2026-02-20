package com.smartats.module.webhook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.webhook.entity.WebhookConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Webhook 配置 Mapper
 */
@Mapper
public interface WebhookConfigMapper extends BaseMapper<WebhookConfig> {
}
