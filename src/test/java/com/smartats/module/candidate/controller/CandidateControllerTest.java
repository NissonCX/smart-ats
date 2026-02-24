package com.smartats.module.candidate.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.SecurityConfig;
import com.smartats.module.auth.filter.JwtAuthenticationFilter;
import com.smartats.module.candidate.dto.CandidateUpdateRequest;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.service.CandidateService;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CandidateController 集成测试
 */
@WebMvcTest(CandidateController.class)
@Import(SecurityConfig.class)
@DisplayName("CandidateController 集成测试")
class CandidateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CandidateService candidateService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private Candidate testCandidate;

    @BeforeEach
    void setUp() throws Exception {
        lenient().doAnswer(invocation -> {
            HttpServletRequest req = invocation.getArgument(0);
            HttpServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter)
                .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));

        testCandidate = new Candidate();
        testCandidate.setId(1L);
        testCandidate.setResumeId(100L);
        testCandidate.setName("张三");
        testCandidate.setPhone("13800138000");
        testCandidate.setEmail("zhangsan@test.com");
        testCandidate.setGender("MALE");
        testCandidate.setAge(28);
        testCandidate.setEducation("本科");
        testCandidate.setSchool("清华大学");
        testCandidate.setWorkYears(5);
        testCandidate.setSkills(List.of("Java", "Spring"));
    }

    private static UsernamePasswordAuthenticationToken mockAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_HR"))
        );
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 根据简历ID查询候选人
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /candidates/resume/{resumeId}")
    class GetByResumeIdTests {

        @Test
        @DisplayName("查询成功（手机号邮箱脱敏）")
        void shouldReturnCandidateWithMaskedData() throws Exception {
            given(candidateService.getByResumeId(100L)).willReturn(testCandidate);

            mockMvc.perform(get("/candidates/resume/100")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("张三"))
                    // 脱敏后手机号和邮箱不应为原始值
                    .andExpect(jsonPath("$.data.phone").value(org.hamcrest.Matchers.containsString("*")))
                    .andExpect(jsonPath("$.data.email").value(org.hamcrest.Matchers.containsString("*")));
        }

        @Test
        @DisplayName("候选人不存在 - 404")
        void shouldReturnErrorWhenNotFound() throws Exception {
            given(candidateService.getByResumeId(999L)).willReturn(null);

            mockMvc.perform(get("/candidates/resume/999")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 查询候选人详情
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /candidates/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("查询成功")
        void shouldReturnCandidate() throws Exception {
            given(candidateService.getById(1L)).willReturn(testCandidate);

            mockMvc.perform(get("/candidates/1")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("张三"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 更新候选人
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("PUT /candidates/{id}")
    class UpdateTests {

        @Test
        @DisplayName("更新成功")
        void shouldUpdateSuccessfully() throws Exception {
            CandidateUpdateRequest request = new CandidateUpdateRequest();
            request.setName("李四");

            Candidate updated = new Candidate();
            updated.setId(1L);
            updated.setName("李四");
            updated.setPhone("13800138000");
            updated.setEmail("test@test.com");

            given(candidateService.updateManual(eq(1L), any(CandidateUpdateRequest.class)))
                    .willReturn(updated);

            mockMvc.perform(put("/candidates/1")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("李四"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 删除候选人
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("DELETE /candidates/{id}")
    class DeleteTests {

        @Test
        @DisplayName("删除成功")
        void shouldDeleteSuccessfully() throws Exception {
            given(candidateService.getById(1L)).willReturn(testCandidate);
            willDoNothing().given(candidateService).deleteById(1L);

            mockMvc.perform(delete("/candidates/1")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("候选人不存在 - 业务异常")
        void shouldReturnErrorWhenNotFound() throws Exception {
            given(candidateService.getById(999L)).willReturn(null);

            mockMvc.perform(delete("/candidates/999")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 候选人列表
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /candidates")
    class ListTests {

        @Test
        @DisplayName("分页查询成功")
        void shouldReturnPagedResults() throws Exception {
            Page<Candidate> page = new Page<>(1, 10);
            page.setTotal(1);
            page.setRecords(List.of(testCandidate));

            given(candidateService.listCandidates(any())).willReturn(page);

            mockMvc.perform(get("/candidates")
                            .param("page", "1")
                            .param("pageSize", "10")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("未认证 - 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/candidates"))
                    .andExpect(status().isForbidden());
        }
    }
}
