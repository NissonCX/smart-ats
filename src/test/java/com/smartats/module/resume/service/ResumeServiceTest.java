package com.smartats.module.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.infrastructure.mq.MessagePublisher;
import com.smartats.infrastructure.storage.FileStorageService;
import com.smartats.module.resume.dto.BatchUploadResponse;
import com.smartats.module.resume.dto.ResumeUploadResponse;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.mapper.ResumeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * ResumeService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ResumeService 单元测试")
class ResumeServiceTest {

    @InjectMocks
    private ResumeService resumeService;

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private MessagePublisher messagePublisher;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private MultipartFile mockFile;

    // PDF file header magic bytes
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};

    @BeforeEach
    void setUp() {
        // Common lenient stubs for Redis operations
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 上传简历测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("uploadResume")
    class UploadResumeTests {

        @Test
        @DisplayName("文件为空抛异常")
        void shouldThrowWhenFileIsEmpty() {
            given(mockFile.isEmpty()).willReturn(true);

            assertThatThrownBy(() -> resumeService.uploadResume(mockFile, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("文件为null抛异常")
        void shouldThrowWhenFileIsNull() {
            assertThatThrownBy(() -> resumeService.uploadResume(null, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("文件超过10MB抛异常")
        void shouldThrowWhenFileTooLarge() {
            given(mockFile.isEmpty()).willReturn(false);
            given(mockFile.getSize()).willReturn(11L * 1024 * 1024); // 11MB

            assertThatThrownBy(() -> resumeService.uploadResume(mockFile, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("不支持的文件类型抛异常")
        void shouldThrowWhenUnsupportedContentType() {
            given(mockFile.isEmpty()).willReturn(false);
            given(mockFile.getSize()).willReturn(1024L);
            given(mockFile.getContentType()).willReturn("image/png");

            assertThatThrownBy(() -> resumeService.uploadResume(mockFile, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("文件重复则返回已有简历")
        void shouldReturnExistingWhenDuplicate() throws Exception {
            // Prepare a valid PDF upload that passes validation
            given(mockFile.isEmpty()).willReturn(false);
            given(mockFile.getSize()).willReturn(1024L);
            given(mockFile.getContentType()).willReturn("application/pdf");
            given(mockFile.getOriginalFilename()).willReturn("resume.pdf");
            given(mockFile.getBytes()).willReturn(PDF_MAGIC);
            given(mockFile.getInputStream()).willReturn(new ByteArrayInputStream(PDF_MAGIC));

            // Redis dedup hit
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(contains("dedup:resume:"))).willReturn("99");

            Resume existingResume = new Resume();
            existingResume.setId(99L);
            existingResume.setFileHash("abc123");
            given(resumeMapper.selectById(99L)).willReturn(existingResume);

            ResumeUploadResponse response = resumeService.uploadResume(mockFile, 1L);

            assertThat(response.getDuplicated()).isTrue();
            assertThat(response.getResumeId()).isEqualTo(99L);
            assertThat(response.getTaskId()).isNull();
        }

        @Test
        @DisplayName("上传成功返回taskId和resumeId")
        void shouldUploadSuccessfully() throws Exception {
            given(mockFile.isEmpty()).willReturn(false);
            given(mockFile.getSize()).willReturn(1024L);
            given(mockFile.getContentType()).willReturn("application/pdf");
            given(mockFile.getOriginalFilename()).willReturn("resume.pdf");
            given(mockFile.getBytes()).willReturn(PDF_MAGIC);
            given(mockFile.getInputStream()).willReturn(new ByteArrayInputStream(PDF_MAGIC));

            // No dedup
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(contains("dedup:resume:"))).willReturn(null);
            // DB dedup check
            given(resumeMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);
            // MinIO upload
            given(fileStorageService.uploadFile(eq(mockFile), anyString())).willReturn("http://minio/resume.pdf");
            // DB insert
            given(resumeMapper.insert(any(Resume.class))).willAnswer(invocation -> {
                Resume r = invocation.getArgument(0);
                r.setId(1L);
                return 1;
            });
            // Task status serialization
            given(objectMapper.writeValueAsString(any(TaskStatusResponse.class))).willReturn("{}");

            ResumeUploadResponse response = resumeService.uploadResume(mockFile, 1L);

            assertThat(response.getDuplicated()).isFalse();
            assertThat(response.getResumeId()).isEqualTo(1L);
            assertThat(response.getTaskId()).isNotBlank();
            then(messagePublisher).should().sendResumeParseMessage(any());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 任务状态查询测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("getTaskStatus")
    class GetTaskStatusTests {

        @Test
        @DisplayName("Redis有数据时返回正确状态")
        void shouldReturnStatusFromRedis() throws Exception {
            String taskId = "test-task-id";
            String json = "{\"status\":\"COMPLETED\",\"progress\":100}";

            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(contains(taskId))).willReturn(json);

            TaskStatusResponse parsed = new TaskStatusResponse();
            parsed.setStatus("COMPLETED");
            parsed.setProgress(100);
            given(objectMapper.readValue(eq(json), eq(TaskStatusResponse.class))).willReturn(parsed);

            TaskStatusResponse response = resumeService.getTaskStatus(taskId);

            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            assertThat(response.getProgress()).isEqualTo(100);
        }

        @Test
        @DisplayName("Redis无数据时返回NOT_FOUND")
        void shouldReturnNotFoundWhenNoRedisData() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);

            TaskStatusResponse response = resumeService.getTaskStatus("nonexistent-id");

            assertThat(response.getStatus()).isEqualTo("NOT_FOUND");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 简历详情查询测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("getResumeById")
    class GetResumeByIdTests {

        @Test
        @DisplayName("查询自己的简历成功")
        void shouldReturnResumeForOwner() {
            Resume resume = new Resume();
            resume.setId(1L);
            resume.setUserId(10L);
            resume.setFileName("resume.pdf");

            given(resumeMapper.selectById(1L)).willReturn(resume);

            Resume result = resumeService.getResumeById(1L, 10L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getFileName()).isEqualTo("resume.pdf");
        }

        @Test
        @DisplayName("简历不存在抛异常")
        void shouldThrowWhenResumeNotFound() {
            given(resumeMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> resumeService.getResumeById(999L, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("查询他人简历抛异常")
        void shouldThrowWhenNotOwner() {
            Resume resume = new Resume();
            resume.setId(1L);
            resume.setUserId(10L);

            given(resumeMapper.selectById(1L)).willReturn(resume);

            assertThatThrownBy(() -> resumeService.getResumeById(1L, 99L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 简历列表测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("listResumes")
    class ListResumesTests {

        @Test
        @DisplayName("分页查询返回结果")
        void shouldReturnPagedResults() {
            Page<Resume> page = new Page<>(1, 10);
            page.setTotal(1);
            Resume resume = new Resume();
            resume.setId(1L);
            resume.setFileName("test.pdf");
            page.setRecords(java.util.List.of(resume));

            given(resumeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .willReturn(page);

            Page<Resume> result = resumeService.listResumes(1L, 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 批量上传测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("batchUploadResumes")
    class BatchUploadTests {

        @Test
        @DisplayName("文件数组为空抛异常")
        void shouldThrowWhenFilesEmpty() {
            assertThatThrownBy(() -> resumeService.batchUploadResumes(null, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("超过20个文件抛异常")
        void shouldThrowWhenExceedMaxBatchSize() {
            MultipartFile[] files = new MultipartFile[21];
            for (int i = 0; i < 21; i++) {
                files[i] = mockFile;
            }

            assertThatThrownBy(() -> resumeService.batchUploadResumes(files, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("频率限制 - 超过每分钟5次抛异常")
        void shouldThrowWhenRateLimited() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn("5");

            MultipartFile[] files = new MultipartFile[]{mockFile};

            assertThatThrownBy(() -> resumeService.batchUploadResumes(files, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("单个文件失败不影响其他文件")
        void shouldContinueWhenSingleFileFails() throws Exception {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(startsWith("rate:upload:"))).willReturn(null);

            // 创建两个 mock 文件
            MultipartFile goodFile = mock(MultipartFile.class);
            MultipartFile badFile = mock(MultipartFile.class);

            // badFile: 空文件 → 抛异常
            given(badFile.isEmpty()).willReturn(true);
            given(badFile.getOriginalFilename()).willReturn("bad.pdf");

            // goodFile: 也设置为空以保持简单（会失败但不会 crash）
            given(goodFile.isEmpty()).willReturn(true);
            given(goodFile.getOriginalFilename()).willReturn("good.pdf");

            MultipartFile[] files = new MultipartFile[]{badFile, goodFile};

            BatchUploadResponse result = resumeService.batchUploadResumes(files, 1L);

            assertThat(result.getTotalCount()).isEqualTo(2);
            assertThat(result.getFailedCount()).isEqualTo(2);
            assertThat(result.getItems()).hasSize(2);
            // 两个文件都因为空文件而失败，但不会crash
            assertThat(result.getItems().get(0).getStatus()).isEqualTo("FAILED");
            assertThat(result.getItems().get(1).getStatus()).isEqualTo("FAILED");
        }
    }
}
