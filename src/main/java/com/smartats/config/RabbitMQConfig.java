package com.smartats.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 交换机
    public static final String RESUME_EXCHANGE = "smartats.exchange";

    // 队列
    public static final String RESUME_PARSE_QUEUE = "resume.parse.queue";

    // 死信队列
    public static final String RESUME_PARSE_DLQ = "resume.parse.dlq";

    // 死信交换机
    public static final String DEAD_LETTER_EXCHANGE = "smartats.dlx";

    // 延迟重试队列（消息过期后自动路由回主队列）
    public static final String RESUME_PARSE_DELAY_QUEUE = "resume.parse.delay";
    public static final String RESUME_PARSE_DELAY_ROUTING_KEY = "resume.parse.delay";

    // 路由键
    public static final String RESUME_PARSE_ROUTING_KEY = "resume.parse";
    public static final String DEAD_LETTER_ROUTING_KEY = "resume.parse.dlq";

    /**
     * JSON 消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置 RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    /**
     * 主交换机
     */
    @Bean
    public DirectExchange resumeExchange() {
        return new DirectExchange(RESUME_EXCHANGE, true, false);
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * 主队列（带死信配置）
     */
    @Bean
    public Queue resumeParseQueue() {
        return QueueBuilder.durable(RESUME_PARSE_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .withArgument("x-max-retries", 3)  // 最大重试次数
                .build();
    }

    /**
     * 延迟重试队列（消息 TTL 过期后通过 DLX 路由回主队列，实现指数退避）
     */
    @Bean
    public Queue resumeParseDelayQueue() {
        return QueueBuilder.durable(RESUME_PARSE_DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", RESUME_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RESUME_PARSE_ROUTING_KEY)
                .build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue resumeParseDLQ() {
        return QueueBuilder.durable(RESUME_PARSE_DLQ).build();
    }

    /**
     * 绑定主队列到主交换机
     */
    @Bean
    public Binding resumeParseBinding() {
        return BindingBuilder
                .bind(resumeParseQueue())
                .to(resumeExchange())
                .with(RESUME_PARSE_ROUTING_KEY);
    }

    /**
     * 绑定延迟重试队列到主交换机
     */
    @Bean
    public Binding delayQueueBinding() {
        return BindingBuilder
                .bind(resumeParseDelayQueue())
                .to(resumeExchange())
                .with(RESUME_PARSE_DELAY_ROUTING_KEY);
    }

    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(resumeParseDLQ())
                .to(deadLetterExchange())
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}