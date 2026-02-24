package com.smartats.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
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
 * <p>
 * Embedding 模型使用智谱 embedding-3，维度 1024，最大 token 8192。
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

    @Value("${smartats.ai.embedding.model:embedding-3}")
    private String embeddingModel;

    /**
     * 创建共享的 OpenAiApi 实例（Chat + Embedding 复用同一 API 客户端）
     */
    @Bean
    public OpenAiApi openAiApi() {
        return new OpenAiApi(
                baseUrl,
                apiKey,
                "/chat/completions",
                "/embeddings",
                RestClient.builder(),
                WebClient.builder(),
                new DefaultResponseErrorHandler()
        );
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxTokens(4000)
                .build();

        return new OpenAiChatModel(openAiApi, options);
    }

    /**
     * 智谱 Embedding 模型（embedding-3）
     * <p>
     * 输出维度：1024
     * 最大输入 Token：8192
     * 用于候选人简历文本向量化，支持语义搜索
     */
    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiApi openAiApi) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .withModel(embeddingModel)
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}