package com.smartats.module.job.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.enums.JobStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.SecurityConfig;
import com.smartats.module.auth.filter.JwtAuthenticationFilter;
import com.smartats.module.job.dto.request.CreateJobRequest;
import com.smartats.module.job.dto.request.UpdateJobRequest;
import com.smartats.module.job.dto.response.JobResponse;
import com.smartats.module.job.service.JobService;
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
 * JobController 集成测试
 * <p>
 * 使用 @WebMvcTest 切片测试，仅加载 Web 层。
 * @Import SecurityConfig 以加载自定义安全规则（permitAll 等）。
 * JwtAuthenticationFilter 被 Mock 为透传，仅测试 Controller + 安全策略。
 */
@WebMvcTest(JobController.class)
@Import(SecurityConfig.class)
@DisplayName("JobController 集成测试")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 让 Mock 的 JwtAuthenticationFilter 透传请求到 FilterChain，
     * 否则 Mockito 默认 void 方法什么都不做，请求会被吞掉。
     */
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

    /**
     * 创建模拟认证（principal=Long userId）
     */
    private static UsernamePasswordAuthenticationToken mockAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_HR"))
        );
    }

    private static JobResponse sampleJobResponse() {
        JobResponse resp = new JobResponse();
        resp.setId(1L);
        resp.setTitle("Java 高级工程师");
        resp.setDepartment("技术部");
        resp.setStatus(JobStatus.PUBLISHED.getCode());
        resp.setStatusDesc("已发布");
        resp.setViewCount(100);
        resp.setCreatedAt(LocalDateTime.of(2026, 2, 24, 10, 0, 0));
        resp.setUpdatedAt(LocalDateTime.of(2026, 2, 24, 10, 0, 0));
        return resp;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 创建职位
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /jobs - 创建职位")
    class CreateJobTests {

        @Test
        @DisplayName("认证用户创建职位 - 200")
        void createJob_authenticated_success() throws Exception {
            CreateJobRequest request = new CreateJobRequest();
            request.setTitle("Java 高级工程师");
            request.setDescription("负责核心系统设计与开发");
            request.setRequirements("5年以上Java经验");
            request.setSalaryMin(25);
            request.setSalaryMax(50);

            given(jobService.createJob(any(CreateJobRequest.class), eq(1L))).willReturn(100L);

            mockMvc.perform(post("/jobs")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(100));
        }

        @Test
        @DisplayName("未认证创建职位 - 401/403")
        void createJob_unauthenticated() throws Exception {
            CreateJobRequest request = new CreateJobRequest();
            request.setTitle("Java 高级工程师");
            request.setDescription("负责核心系统设计与开发");
            request.setRequirements("5年以上Java经验");
            request.setSalaryMin(25);
            request.setSalaryMax(50);

            mockMvc.perform(post("/jobs")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("参数校验失败 - 缺少必填字段")
        void createJob_validationFail() throws Exception {
            CreateJobRequest request = new CreateJobRequest();
            // title 和 description 为空，违反 @NotBlank

            mockMvc.perform(post("/jobs")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())  // @ResponseStatus(BAD_REQUEST)
                    .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.getCode()));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 获取职位详情（公开接口）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /jobs/{id} - 职位详情（公开）")
    class GetJobDetailTests {

        @Test
        @DisplayName("匿名访问公开职位 - 200")
        void getJobDetail_anonymous_success() throws Exception {
            given(jobService.getJobDetail(1L)).willReturn(sampleJobResponse());

            mockMvc.perform(get("/jobs/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("Java 高级工程师"))
                    .andExpect(jsonPath("$.data.viewCount").value(100));
        }

        @Test
        @DisplayName("职位不存在 - 业务异常")
        void getJobDetail_notFound() throws Exception {
            given(jobService.getJobDetail(999L))
                    .willThrow(new BusinessException(ResultCode.NOT_FOUND, "职位不存在"));

            mockMvc.perform(get("/jobs/999"))
                    .andExpect(status().isOk())  // GlobalExceptionHandler 返回 200 body
                    .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value("职位不存在"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 发布/关闭/删除职位
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /jobs/{id}/publish & close, DELETE /jobs/{id}")
    class StateChangeTests {

        @Test
        @DisplayName("发布职位 - 200")
        void publishJob_success() throws Exception {
            willDoNothing().given(jobService).publishJob(1L, 1L);

            mockMvc.perform(post("/jobs/1/publish")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("关闭职位 - 200")
        void closeJob_success() throws Exception {
            willDoNothing().given(jobService).closeJob(1L, 1L);

            mockMvc.perform(post("/jobs/1/close")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("删除职位 - 200")
        void deleteJob_success() throws Exception {
            willDoNothing().given(jobService).deleteJob(1L, 1L);

            mockMvc.perform(delete("/jobs/1")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("无权限发布 - 业务异常")
        void publishJob_forbidden() throws Exception {
            willThrow(new BusinessException(ResultCode.FORBIDDEN, "无权限操作此职位"))
                    .given(jobService).publishJob(1L, 1L);

            mockMvc.perform(post("/jobs/1/publish")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())  // GlobalExceptionHandler 处理
                    .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 热门职位（公开接口）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /jobs/hot - 热门职位（公开）")
    class HotJobsTests {

        @Test
        @DisplayName("匿名获取热门职位 - 200")
        void getHotJobs_success() throws Exception {
            given(jobService.getHotJobs(10)).willReturn(List.of(sampleJobResponse()));

            mockMvc.perform(get("/jobs/hot").param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].title").value("Java 高级工程师"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 更新职位
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("PUT /jobs - 更新职位")
    class UpdateJobTests {

        @Test
        @DisplayName("正常更新 - 200")
        void updateJob_success() throws Exception {
            UpdateJobRequest request = new UpdateJobRequest();
            request.setId(1L);
            request.setTitle("更新后的标题");

            willDoNothing().given(jobService).updateJob(any(UpdateJobRequest.class), eq(1L));

            mockMvc.perform(put("/jobs")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 职位列表（公开接口）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /jobs - 职位列表（公开）")
    class JobListTests {

        @Test
        @DisplayName("匿名分页查询 - 200")
        void listJobs_success() throws Exception {
            Page<JobResponse> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(sampleJobResponse()));

            given(jobService.getJobList(any())).willReturn(page);

            mockMvc.perform(get("/jobs")
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].title").value("Java 高级工程师"));
        }
    }
}
