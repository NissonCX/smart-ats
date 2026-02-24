package com.smartats.module.resume.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.SecurityConfig;
import com.smartats.module.auth.filter.JwtAuthenticationFilter;
import com.smartats.module.resume.dto.BatchUploadResponse;
import com.smartats.module.resume.dto.BatchUploadResponse.BatchUploadItem;
import com.smartats.module.resume.dto.ResumeUploadResponse;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.service.ResumeService;
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
import org.springframework.mock.web.MockMultipartFile;
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
 * ResumeController 集成测试
 */
@WebMvcTest(ResumeController.class)
@Import(SecurityConfig.class)
@DisplayName("ResumeController 集成测试")
class ResumeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResumeService resumeService;

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

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 上传简历
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /resumes/upload")
    class UploadTests {

        @Test
        @DisplayName("上传成功 - 200")
        void shouldUploadSuccessfully() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "resume.pdf", "application/pdf", "pdf-content".getBytes());

            ResumeUploadResponse response = new ResumeUploadResponse("task-123", 1L, false, "上传成功");
            given(resumeService.uploadResume(any(), eq(1L))).willReturn(response);

            mockMvc.perform(multipart("/resumes/upload")
                            .file(file)
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.taskId").value("task-123"))
                    .andExpect(jsonPath("$.data.resumeId").value(1));
        }

        @Test
        @DisplayName("未认证 - 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "resume.pdf", "application/pdf", "pdf-content".getBytes());

            mockMvc.perform(multipart("/resumes/upload")
                            .file(file)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 查询任务状态
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /resumes/tasks/{taskId}")
    class TaskStatusTests {

        @Test
        @DisplayName("查询任务状态成功")
        void shouldReturnTaskStatus() throws Exception {
            TaskStatusResponse response = new TaskStatusResponse();
            response.setStatus("COMPLETED");
            response.setResumeId(1L);
            response.setCandidateId(1L);
            response.setProgress(100);
            given(resumeService.getTaskStatus("task-123")).willReturn(response);

            mockMvc.perform(get("/resumes/tasks/task-123")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.progress").value(100));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 获取简历详情
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /resumes/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("获取简历详情成功")
        void shouldReturnResume() throws Exception {
            Resume resume = new Resume();
            resume.setId(1L);
            resume.setUserId(1L);
            resume.setFileName("resume.pdf");
            resume.setStatus("COMPLETED");

            given(resumeService.getResumeById(1L, 1L)).willReturn(resume);

            mockMvc.perform(get("/resumes/1")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.fileName").value("resume.pdf"));
        }

        @Test
        @DisplayName("简历不存在 - 业务异常")
        void shouldReturnErrorWhenNotFound() throws Exception {
            given(resumeService.getResumeById(eq(999L), eq(1L)))
                    .willThrow(new BusinessException(ResultCode.RESUME_NOT_FOUND));

            mockMvc.perform(get("/resumes/999")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 简历列表
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /resumes")
    class ListTests {

        @Test
        @DisplayName("分页查询成功")
        void shouldReturnPagedResults() throws Exception {
            Page<Resume> page = new Page<>(1, 10);
            page.setTotal(1);
            Resume resume = new Resume();
            resume.setId(1L);
            resume.setFileName("resume.pdf");
            page.setRecords(List.of(resume));

            given(resumeService.listResumes(1L, 1, 10)).willReturn(page);

            mockMvc.perform(get("/resumes")
                            .param("page", "1")
                            .param("size", "10")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 批量上传
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /resumes/batch-upload")
    class BatchUploadTests {

        @Test
        @DisplayName("批量上传成功 - 200")
        void shouldBatchUploadSuccessfully() throws Exception {
            BatchUploadResponse response = new BatchUploadResponse(2, 2, 0, List.of(
                    new BatchUploadItem("task-1", 1L, "resume1.pdf", "QUEUED", "上传成功"),
                    new BatchUploadItem("task-2", 2L, "resume2.pdf", "QUEUED", "上传成功")
            ));
            given(resumeService.batchUploadResumes(any(), eq(1L))).willReturn(response);

            MockMultipartFile file1 = new MockMultipartFile(
                    "files", "resume1.pdf", "application/pdf", "pdf-content-1".getBytes());
            MockMultipartFile file2 = new MockMultipartFile(
                    "files", "resume2.pdf", "application/pdf", "pdf-content-2".getBytes());

            mockMvc.perform(multipart("/resumes/batch-upload")
                            .file(file1)
                            .file(file2)
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.totalCount").value(2))
                    .andExpect(jsonPath("$.data.successCount").value(2))
                    .andExpect(jsonPath("$.data.failedCount").value(0))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items[0].taskId").value("task-1"));
        }

        @Test
        @DisplayName("未认证 - 403")
        void shouldReturn403WhenNotAuthenticated() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "resume.pdf", "application/pdf", "pdf-content".getBytes());

            mockMvc.perform(multipart("/resumes/batch-upload")
                            .file(file)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }
}
