package com.smartats.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 智谱 AI 配置（使用 OpenAI 兼容模式）
 * <p>
 * 注意：智谱 API 路径与 OpenAI 不同：
 * - OpenAI 默认路径：/v1/chat/completions
 * - 智谱 AI 路径：/chat/completions（baseUrl 已含 /v4）
 * <p>
 * 最终请求 URL = baseUrl + completionsPath
 *   = https://open.bigmodel.cn/api/paas/v4/chat/completions
 */
@Configuration
public class ZhipuAiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:glm-4-flash-250414}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private Double temperature;

    @Bean
    public OpenAiChatModel openAiChatModel() {
        // 使用自定义 completionsPath，覆盖 Spring AI 默认的 /v1/chat/completions
        // 智谱 AI 实际的聊天接口路径是 /chat/completions（不含 /v1 前缀）
        OpenAiApi openAiApi = new OpenAiApi(
                baseUrl,
                apiKey,
                "/chat/completions",
                "/v1/embeddings",
                RestClient.builder(),
                WebClient.builder(),
                new DefaultResponseErrorHandler()
        );

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxTokens(4000)
                .build();

        return new OpenAiChatModel(openAiApi, options);
    }
}