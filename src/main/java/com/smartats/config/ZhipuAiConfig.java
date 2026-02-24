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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

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
     * <p>
     * 设置合理的超时时间：AI 简历解析通常需要 15-45 秒，
     * 默认超时太短会导致 "Request timed out" 错误。
     */
    @Bean
    public OpenAiApi openAiApi() {
        // 配置 HTTP 超时：连接 30 秒，读取 120 秒（AI 生成内容耗时较长）
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(30));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        return new OpenAiApi(
                baseUrl,
                apiKey,
                "/chat/completions",
                "/embeddings",
                restClientBuilder,
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

        // 禁用 Spring AI 内部重试（默认 maxAttempts=10, exponentialBackoff 2s~180s）
        // 我们在 MQ 消费层通过延迟队列实现更合理的指数退避重试（10s/30s/60s）
        RetryTemplate noRetry = RetryTemplate.builder()
                .maxAttempts(1)
                .build();

        return new OpenAiChatModel(openAiApi, options, null, noRetry);
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
                .withDimensions(1024)  // embedding-3 默认 2048 维，指定 1024 与 Milvus schema 一致
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}