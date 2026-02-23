package com.smartats.infrastructure.mq;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送简历解析消息
     */
    public void sendResumeParseMessage(Object message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.RESUME_EXCHANGE,
                    RabbitMQConfig.RESUME_PARSE_ROUTING_KEY,
                    message
            );

            log.info("发送简历解析消息成功: {}", message);

        } catch (Exception e) {
            log.error("发送简历解析消息失败", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "消息发送失败");
        }
    }
}