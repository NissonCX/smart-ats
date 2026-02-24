package com.smartats.module.webhook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.SecurityConfig;
import com.smartats.module.auth.filter.JwtAuthenticationFilter;
import com.smartats.module.webhook.dto.WebhookCreateRequest;
import com.smartats.module.webhook.dto.WebhookResponse;
import com.smartats.module.webhook.service.WebhookService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebhookController 集成测试
 */
@WebMvcTest(WebhookController.class)
@Import(SecurityConfig.class)
@DisplayName("WebhookController 集成测试")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookService webhookService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void configureMockFilter() throws Exception {
        lenient().doAnswer(invocation -> {
            HttpServletRequest req = invocation.getArgument(0);
            HttpServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter)
                .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));
    }

    private static UsernamePasswordAuthenticationToken mockAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_HR"))
        );
    }

    private static WebhookResponse sampleWebhookResponse() {
        return WebhookResponse.builder()
                .id(1L)
                .url("https://example.com/webhook")
                .events(List.of("resume.uploaded", "candidate.created"))
                .description("测试 Webhook")
                .enabled(true)
                .failureCount(0)
                .secretHint("abcd****5678")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 创建 Webhook
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /webhooks")
    class CreateTests {

        @Test
        @DisplayName("创建成功 - 200")
        void shouldCreateSuccessfully() throws Exception {
            WebhookCreateRequest request = new WebhookCreateRequest();
            request.setUrl("https://example.com/hook");
            request.setEvents(List.of("resume.uploaded"));
            request.setDescription("My Webhook");

            given(webhookService.createWebhook(eq(1L), any(WebhookCreateRequest.class)))
                    .willReturn(sampleWebhookResponse());

            mockMvc.perform(post("/webhooks")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.url").value("https://example.com/webhook"))
                    .andExpect(jsonPath("$.data.enabled").value(true));
        }

        @Test
        @DisplayName("未认证 - 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            WebhookCreateRequest request = new WebhookCreateRequest();
            request.setUrl("https://example.com/hook");
            request.setEvents(List.of("resume.uploaded"));

            mockMvc.perform(post("/webhooks")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 获取 Webhook 列表
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /webhooks")
    class ListTests {

        @Test
        @DisplayName("获取列表成功")
        void shouldReturnWebhookList() throws Exception {
            given(webhookService.getUserWebhooks(1L))
                    .willReturn(List.of(sampleWebhookResponse()));

            mockMvc.perform(get("/webhooks")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].url").value("https://example.com/webhook"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 删除 Webhook
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("DELETE /webhooks/{id}")
    class DeleteTests {

        @Test
        @DisplayName("删除成功")
        void shouldDeleteSuccessfully() throws Exception {
            willDoNothing().given(webhookService).deleteWebhook(1L, 1L);

            mockMvc.perform(delete("/webhooks/1")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 测试 Webhook
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /webhooks/{id}/test")
    class TestWebhookTests {

        @Test
        @DisplayName("测试成功")
        void shouldReturnTrue() throws Exception {
            given(webhookService.testWebhook(1L, 1L)).willReturn(true);

            mockMvc.perform(post("/webhooks/1/test")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("Webhook不存在 - 业务异常")
        void shouldReturnErrorWhenNotFound() throws Exception {
            given(webhookService.testWebhook(1L, 999L))
                    .willThrow(new BusinessException(ResultCode.NOT_FOUND, "Webhook 不存在"));

            mockMvc.perform(post("/webhooks/999/test")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
        }
    }
}
