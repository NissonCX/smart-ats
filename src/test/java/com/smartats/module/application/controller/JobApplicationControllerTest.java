package com.smartats.module.application.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.SecurityConfig;
import com.smartats.module.application.dto.*;
import com.smartats.module.application.service.JobApplicationService;
import com.smartats.module.auth.filter.JwtAuthenticationFilter;
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

import java.math.BigDecimal;
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
 * JobApplicationController 集成测试
 * <p>
 * 所有申请接口均需认证（anyRequest().authenticated()）。
 * 使用 @Import SecurityConfig 加载自定义安全规则。
 */
@WebMvcTest(JobApplicationController.class)
@Import(SecurityConfig.class)
@DisplayName("JobApplicationController 集成测试")
class JobApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobApplicationService jobApplicationService;

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

    // ━━━━━━━━━━━━━━ 工具方法 ━━━━━━━━━━━━━━

    private static UsernamePasswordAuthenticationToken mockAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_HR"))
        );
    }

    private static ApplicationResponse sampleResponse() {
        ApplicationResponse resp = new ApplicationResponse();
        resp.setId(1L);
        resp.setJobId(10L);
        resp.setJobTitle("Java 高级工程师");
        resp.setCandidateId(20L);
        resp.setCandidateName("张三");
        resp.setStatus("PENDING");
        resp.setStatusDesc("待筛选");
        resp.setMatchScore(new BigDecimal("85.5"));
        resp.setMatchReasons(List.of("技能匹配度高", "经验符合要求"));
        resp.setAppliedAt(LocalDateTime.of(2026, 2, 24, 10, 0, 0));
        resp.setUpdatedAt(LocalDateTime.of(2026, 2, 24, 10, 0, 0));
        return resp;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 创建申请
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /applications - 创建申请")
    class CreateApplicationTests {

        @Test
        @DisplayName("认证用户创建申请 - 200")
        void create_authenticated_success() throws Exception {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(10L);
            request.setCandidateId(20L);

            given(jobApplicationService.createApplication(any(CreateApplicationRequest.class)))
                    .willReturn(1L);

            mockMvc.perform(post("/applications")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(1));
        }

        @Test
        @DisplayName("未认证创建申请 - 401/403")
        void create_unauthenticated() throws Exception {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(10L);
            request.setCandidateId(20L);

            mockMvc.perform(post("/applications")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("参数校验失败 - 缺少 jobId")
        void create_validationFail() throws Exception {
            CreateApplicationRequest request = new CreateApplicationRequest();
            // jobId 和 candidateId 为空，违反 @NotNull

            mockMvc.perform(post("/applications")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.getCode()));
        }

        @Test
        @DisplayName("重复申请 - 业务异常")
        void create_duplicate() throws Exception {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(10L);
            request.setCandidateId(20L);

            given(jobApplicationService.createApplication(any(CreateApplicationRequest.class)))
                    .willThrow(new BusinessException(ResultCode.BAD_REQUEST, "该候选人已申请过此职位"));

            mockMvc.perform(post("/applications")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.getCode()))
                    .andExpect(jsonPath("$.message").value("该候选人已申请过此职位"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 更新申请状态
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("PUT /applications/{id}/status - 更新状态")
    class UpdateStatusTests {

        @Test
        @DisplayName("合法状态变更 - 200")
        void updateStatus_success() throws Exception {
            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus("SCREENING");
            request.setHrNotes("初步筛选通过");

            willDoNothing().given(jobApplicationService).updateStatus(eq(1L), any(UpdateApplicationStatusRequest.class));

            mockMvc.perform(put("/applications/1/status")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("非法状态变更 - 业务异常")
        void updateStatus_invalidTransition() throws Exception {
            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus("OFFER");

            willThrow(new BusinessException(ResultCode.APPLICATION_STATUS_INVALID, "不允许从当前状态变更"))
                    .given(jobApplicationService).updateStatus(eq(1L), any(UpdateApplicationStatusRequest.class));

            mockMvc.perform(put("/applications/1/status")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.APPLICATION_STATUS_INVALID.getCode()));
        }

        @Test
        @DisplayName("参数校验 - 非法状态值")
        void updateStatus_invalidValue() throws Exception {
            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus("INVALID");  // 不在 @Pattern 允许值中

            mockMvc.perform(put("/applications/1/status")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.getCode()));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 查询
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /applications - 查询")
    class QueryTests {

        @Test
        @DisplayName("查询申请详情 - 200")
        void getById_success() throws Exception {
            given(jobApplicationService.getById(1L)).willReturn(sampleResponse());

            mockMvc.perform(get("/applications/1")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.jobTitle").value("Java 高级工程师"))
                    .andExpect(jsonPath("$.data.candidateName").value("张三"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("申请不存在 - 业务异常")
        void getById_notFound() throws Exception {
            given(jobApplicationService.getById(999L))
                    .willThrow(new BusinessException(ResultCode.NOT_FOUND, "申请记录不存在"));

            mockMvc.perform(get("/applications/999")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value("申请记录不存在"));
        }

        @Test
        @DisplayName("按职位查询申请列表 - 200")
        void listByJobId_success() throws Exception {
            Page<ApplicationResponse> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(sampleResponse()));

            given(jobApplicationService.listByJobId(10L, 1, 10)).willReturn(page);

            mockMvc.perform(get("/applications/job/10")
                            .with(authentication(mockAuth(1L)))
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].jobId").value(10));
        }

        @Test
        @DisplayName("按候选人查询申请列表 - 200")
        void listByCandidateId_success() throws Exception {
            Page<ApplicationResponse> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(sampleResponse()));

            given(jobApplicationService.listByCandidateId(20L, 1, 10)).willReturn(page);

            mockMvc.perform(get("/applications/candidate/20")
                            .with(authentication(mockAuth(1L)))
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].candidateId").value(20));
        }

        @Test
        @DisplayName("综合查询申请列表 - 200")
        void listApplications_success() throws Exception {
            Page<ApplicationResponse> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(sampleResponse()));

            given(jobApplicationService.listApplications(any(ApplicationQueryRequest.class))).willReturn(page);

            mockMvc.perform(get("/applications")
                            .with(authentication(mockAuth(1L)))
                            .param("pageNum", "1")
                            .param("pageSize", "10")
                            .param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isArray());
        }
    }
}
