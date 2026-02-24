package com.smartats.module.webhook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.module.webhook.dto.WebhookCreateRequest;
import com.smartats.module.webhook.dto.WebhookResponse;
import com.smartats.module.webhook.entity.WebhookConfig;
import com.smartats.module.webhook.entity.WebhookLog;
import com.smartats.module.webhook.enums.WebhookEventType;
import com.smartats.module.webhook.mapper.WebhookConfigMapper;
import com.smartats.module.webhook.mapper.WebhookLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * WebhookService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookService 单元测试")
class WebhookServiceTest {

    @InjectMocks
    private WebhookService webhookService;

    @Mock
    private WebhookConfigMapper webhookConfigMapper;
    @Mock
    private WebhookLogMapper webhookLogMapper;
    @Mock
    private ObjectMapper objectMapper;

    private WebhookConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = new WebhookConfig();
        testConfig.setId(1L);
        testConfig.setUserId(10L);
        testConfig.setUrl("https://example.com/webhook");
        testConfig.setEvents("resume.uploaded,candidate.created");
        testConfig.setSecret("abcd1234efgh5678abcd1234efgh5678");
        testConfig.setEnabled(true);
        testConfig.setDescription("测试 Webhook");
        testConfig.setFailureCount(0);
        testConfig.setCreatedAt(LocalDateTime.now());
        testConfig.setUpdatedAt(LocalDateTime.now());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 创建 Webhook
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("createWebhook")
    class CreateWebhookTests {

        @Test
        @DisplayName("创建成功")
        void shouldCreateSuccessfully() {
            WebhookCreateRequest request = new WebhookCreateRequest();
            request.setUrl("https://example.com/hook");
            request.setEvents(List.of("resume.uploaded", "candidate.created"));
            request.setDescription("新 Webhook");

            given(webhookConfigMapper.insert(any(WebhookConfig.class))).willAnswer(invocation -> {
                WebhookConfig c = invocation.getArgument(0);
                c.setId(1L);
                return 1;
            });

            WebhookResponse response = webhookService.createWebhook(10L, request);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUrl()).isEqualTo("https://example.com/hook");
            assertThat(response.getEvents()).containsExactly("resume.uploaded", "candidate.created");

            ArgumentCaptor<WebhookConfig> captor = ArgumentCaptor.forClass(WebhookConfig.class);
            then(webhookConfigMapper).should().insert(captor.capture());
            WebhookConfig saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(10L);
            assertThat(saved.getSecret()).isNotBlank();
            assertThat(saved.getEnabled()).isTrue();
            assertThat(saved.getFailureCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("无效事件类型抛异常")
        void shouldThrowWhenInvalidEventType() {
            WebhookCreateRequest request = new WebhookCreateRequest();
            request.setUrl("https://example.com/hook");
            request.setEvents(List.of("invalid.event"));

            assertThatThrownBy(() -> webhookService.createWebhook(10L, request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("混合有效和无效事件类型抛异常")
        void shouldThrowWhenMixedEventTypes() {
            WebhookCreateRequest request = new WebhookCreateRequest();
            request.setUrl("https://example.com/hook");
            request.setEvents(List.of("resume.uploaded", "totally.bogus"));

            assertThatThrownBy(() -> webhookService.createWebhook(10L, request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 查询用户 Webhooks
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("getUserWebhooks")
    class GetUserWebhooksTests {

        @Test
        @DisplayName("返回用户的所有Webhook配置")
        void shouldReturnUserWebhooks() {
            given(webhookConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                    .willReturn(List.of(testConfig));

            List<WebhookResponse> result = webhookService.getUserWebhooks(10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUrl()).isEqualTo("https://example.com/webhook");
            assertThat(result.get(0).getSecretHint()).isNotBlank();
        }

        @Test
        @DisplayName("无配置时返回空列表")
        void shouldReturnEmptyListWhenNoWebhooks() {
            given(webhookConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                    .willReturn(List.of());

            List<WebhookResponse> result = webhookService.getUserWebhooks(10L);

            assertThat(result).isEmpty();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 删除 Webhook
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("deleteWebhook")
    class DeleteWebhookTests {

        @Test
        @DisplayName("删除成功")
        void shouldDeleteSuccessfully() {
            given(webhookConfigMapper.delete(any(LambdaQueryWrapper.class))).willReturn(1);

            webhookService.deleteWebhook(10L, 1L);

            then(webhookConfigMapper).should().delete(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("删除不存在的webhook不抛异常")
        void shouldNotThrowWhenNotExist() {
            given(webhookConfigMapper.delete(any(LambdaQueryWrapper.class))).willReturn(0);

            assertThatCode(() -> webhookService.deleteWebhook(10L, 999L))
                    .doesNotThrowAnyException();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 测试 Webhook
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("testWebhook")
    class TestWebhookTests {

        @Test
        @DisplayName("Webhook不存在抛异常")
        void shouldThrowWhenWebhookNotFound() {
            given(webhookConfigMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);

            assertThatThrownBy(() -> webhookService.testWebhook(10L, 999L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // toResponse 转换
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("toResponse 转换")
    class ToResponseTests {

        @Test
        @DisplayName("secret脱敏正确显示前4后4")
        void shouldMaskSecretCorrectly() {
            given(webhookConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                    .willReturn(List.of(testConfig));

            List<WebhookResponse> result = webhookService.getUserWebhooks(10L);

            String secretHint = result.get(0).getSecretHint();
            assertThat(secretHint).startsWith("abcd");
            assertThat(secretHint).endsWith("5678");
            assertThat(secretHint).contains("****");
        }

        @Test
        @DisplayName("events逗号分隔正确转为List")
        void shouldSplitEventsCorrectly() {
            given(webhookConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                    .willReturn(List.of(testConfig));

            List<WebhookResponse> result = webhookService.getUserWebhooks(10L);

            assertThat(result.get(0).getEvents())
                    .containsExactly("resume.uploaded", "candidate.created");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // WebhookEventType 枚举测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("WebhookEventType")
    class EventTypeTests {

        @Test
        @DisplayName("有效事件码解析成功")
        void shouldParseValidEventCode() {
            assertThat(WebhookEventType.fromCode("resume.uploaded"))
                    .isEqualTo(WebhookEventType.RESUME_UPLOADED);
            assertThat(WebhookEventType.fromCode("interview.scheduled"))
                    .isEqualTo(WebhookEventType.INTERVIEW_SCHEDULED);
        }

        @Test
        @DisplayName("无效事件码返回null")
        void shouldReturnNullForInvalidCode() {
            assertThat(WebhookEventType.fromCode("invalid.event")).isNull();
            assertThat(WebhookEventType.fromCode("")).isNull();
            assertThat(WebhookEventType.fromCode(null)).isNull();
        }

        @Test
        @DisplayName("共有12种事件类型")
        void shouldHave12EventTypes() {
            assertThat(WebhookEventType.values()).hasSize(12);
        }
    }
}
