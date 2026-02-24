package com.smartats.module.webhook.controller;

import com.smartats.common.annotation.AuditLog;
import com.smartats.common.result.Result;
import com.smartats.module.webhook.dto.WebhookCreateRequest;
import com.smartats.module.webhook.dto.WebhookResponse;
import com.smartats.module.webhook.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Webhook 管理", description = "Webhook 配置 CRUD、13 种事件类型、HMAC-SHA256 签名、异步发送")
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
    @Operation(summary = "创建 Webhook 配置", description = "自动生成 HMAC 密钥")
    @AuditLog(module = "Webhook", operation = "CREATE", description = "创建 Webhook 配置")
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
    @Operation(summary = "获取用户的所有 Webhook 配置")
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
    @Operation(summary = "删除 Webhook 配置")
    @AuditLog(module = "Webhook", operation = "DELETE", description = "删除 Webhook 配置")
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
    @Operation(summary = "测试 Webhook", description = "发送测试事件，同步返回发送结果")
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
