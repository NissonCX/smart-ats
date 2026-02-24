package com.smartats.module.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.enums.InterviewStatus;
import com.smartats.common.enums.InterviewType;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.SecurityConfig;
import com.smartats.module.auth.filter.JwtAuthenticationFilter;
import com.smartats.module.interview.dto.InterviewResponse;
import com.smartats.module.interview.dto.ScheduleInterviewRequest;
import com.smartats.module.interview.dto.SubmitFeedbackRequest;
import com.smartats.module.interview.service.InterviewService;
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
 * InterviewController 集成测试
 * <p>
 * @Import SecurityConfig 以加载自定义安全规则。
 * JwtAuthenticationFilter 被 Mock 为透传。
 */
@WebMvcTest(InterviewController.class)
@Import(SecurityConfig.class)
@DisplayName("InterviewController 集成测试")
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InterviewService interviewService;

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

    private static InterviewResponse sampleResponse() {
        InterviewResponse resp = new InterviewResponse();
        resp.setId(1L);
        resp.setApplicationId(10L);
        resp.setInterviewerId(100L);
        resp.setRound(1);
        resp.setInterviewType(InterviewType.VIDEO.getCode());
        resp.setStatus(InterviewStatus.SCHEDULED.getCode());
        resp.setScheduledAt(LocalDateTime.of(2026, 3, 1, 14, 0, 0));
        return resp;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 安排面试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /interviews - 安排面试")
    class ScheduleTests {

        @Test
        @DisplayName("正常安排面试 - 200")
        void schedule_success() throws Exception {
            ScheduleInterviewRequest request = new ScheduleInterviewRequest();
            request.setApplicationId(10L);
            request.setInterviewerId(100L);
            request.setInterviewType(InterviewType.VIDEO.getCode());
            request.setScheduledAt(LocalDateTime.of(2026, 3, 1, 14, 0, 0));

            given(interviewService.scheduleInterview(any(ScheduleInterviewRequest.class))).willReturn(1L);

            mockMvc.perform(post("/interviews")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(1));
        }

        @Test
        @DisplayName("未认证 - 401/403")
        void schedule_unauthenticated() throws Exception {
            ScheduleInterviewRequest request = new ScheduleInterviewRequest();
            request.setApplicationId(10L);
            request.setInterviewerId(100L);
            request.setInterviewType(InterviewType.VIDEO.getCode());
            request.setScheduledAt(LocalDateTime.of(2026, 3, 1, 14, 0, 0));

            mockMvc.perform(post("/interviews")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 提交反馈
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("PUT /interviews/{id}/feedback")
    class FeedbackTests {

        @Test
        @DisplayName("提交反馈 - 200")
        void feedback_success() throws Exception {
            SubmitFeedbackRequest request = new SubmitFeedbackRequest();
            request.setScore(8);
            request.setFeedback("候选人表现优秀");
            request.setRecommendation("YES");

            willDoNothing().given(interviewService).submitFeedback(eq(1L), any(SubmitFeedbackRequest.class));

            mockMvc.perform(put("/interviews/1/feedback")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 取消面试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /interviews/{id}/cancel")
    class CancelTests {

        @Test
        @DisplayName("取消面试 - 200")
        void cancel_success() throws Exception {
            willDoNothing().given(interviewService).cancelInterview(1L);

            mockMvc.perform(post("/interviews/1/cancel")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("取消不存在的面试 - 业务异常")
        void cancel_notFound() throws Exception {
            willThrow(new BusinessException(ResultCode.NOT_FOUND, "面试记录不存在"))
                    .given(interviewService).cancelInterview(999L);

            mockMvc.perform(post("/interviews/999/cancel")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value("面试记录不存在"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 查询
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /interviews - 查询")
    class QueryTests {

        @Test
        @DisplayName("查询面试详情 - 200")
        void getById_success() throws Exception {
            given(interviewService.getById(1L)).willReturn(sampleResponse());

            mockMvc.perform(get("/interviews/1")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.round").value(1))
                    .andExpect(jsonPath("$.data.interviewType").value("VIDEO"));
        }

        @Test
        @DisplayName("按申请查询面试列表 - 200")
        void listByApplication_success() throws Exception {
            given(interviewService.listByApplicationId(10L)).willReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/interviews/application/10")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].applicationId").value(10));
        }
    }
}
