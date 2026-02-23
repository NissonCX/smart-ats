package com.smartats.module.webhook.controller;

import com.smartats.common.result.Result;
import com.smartats.module.webhook.dto.WebhookCreateRequest;
import com.smartats.module.webhook.dto.WebhookResponse;
import com.smartats.module.webhook.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Webhook 管理接口
 * <p>
 * 统一通过 Spring Security 的 Authentication.getPrincipal() 获取 userId，
 * 与其他 Controller（JobController、CandidateController）保持一致。
 */
@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    /**
     * 创建 Webhook 配置
     * POST /api/v1/webhooks
     */
    @PostMapping
    public Result<WebhookResponse> createWebhook(
            @Valid @RequestBody WebhookCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        WebhookResponse response = webhookService.createWebhook(userId, request);

        log.info("创建 Webhook: userId={}, url={}", userId, request.getUrl());

        return Result.success(response);
    }

    /**
     * 获取用户的所有 Webhook 配置
     * GET /api/v1/webhooks
     */
    @GetMapping
    public Result<List<WebhookResponse>> getWebhooks(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        List<WebhookResponse> webhooks = webhookService.getUserWebhooks(userId);

        return Result.success(webhooks);
    }

    /**
     * 删除 Webhook 配置
     * DELETE /api/v1/webhooks/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteWebhook(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        webhookService.deleteWebhook(userId, id);

        log.info("删除 Webhook: userId={}, webhookId={}", userId, id);

        return Result.success();
    }

    /**
     * 测试 Webhook（发送测试事件，同步返回结果）
     * POST /api/v1/webhooks/{id}/test
     */
    @PostMapping("/{id}/test")
    public Result<Boolean> testWebhook(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        boolean success = webhookService.testWebhook(userId, id);

        log.info("测试 Webhook: userId={}, webhookId={}, success={}", userId, id, success);

        return Result.success(success);
    }
}
