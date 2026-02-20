package com.smartats.module.webhook.controller;

import com.smartats.common.result.Result;
import com.smartats.module.auth.util.JwtUtil;
import com.smartats.module.webhook.dto.WebhookCreateRequest;
import com.smartats.module.webhook.dto.WebhookResponse;
import com.smartats.module.webhook.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Webhook 管理接口
 */
@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final JwtUtil jwtUtil;

    /**
     * 创建 Webhook 配置
     * POST /api/v1/webhooks
     */
    @PostMapping
    public Result<WebhookResponse> createWebhook(
            @Valid @RequestBody WebhookCreateRequest request,
            HttpServletRequest httpRequest) {
        // 从 JWT 中获取用户 ID
        Long userId = jwtUtil.getUserIdFromToken(httpRequest);

        WebhookResponse response = webhookService.createWebhook(userId, request);

        log.info("创建 Webhook: userId={}, url={}", userId, request.getUrl());

        return Result.success(response);
    }

    /**
     * 获取用户的所有 Webhook 配置
     * GET /api/v1/webhooks
     */
    @GetMapping
    public Result<List<WebhookResponse>> getWebhooks(HttpServletRequest httpRequest) {
        Long userId = jwtUtil.getUserIdFromToken(httpRequest);

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
            HttpServletRequest httpRequest) {
        Long userId = jwtUtil.getUserIdFromToken(httpRequest);

        webhookService.deleteWebhook(userId, id);

        log.info("删除 Webhook: userId={}, webhookId={}", userId, id);

        return Result.success();
    }

    /**
     * 测试 Webhook（发送测试事件）
     * POST /api/v1/webhooks/{id}/test
     */
    @PostMapping("/{id}/test")
    public Result<Void> testWebhook(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = jwtUtil.getUserIdFromToken(httpRequest);

        // TODO: 实现测试功能
        // 发送一个测试事件到指定的 Webhook

        log.info("测试 Webhook: userId={}, webhookId={}", userId, id);

        return Result.success();
    }
}
