package com.smartats.module.application.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.enums.ApplicationStatus;
import com.smartats.common.enums.JobStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.module.application.dto.CreateApplicationRequest;
import com.smartats.module.application.dto.UpdateApplicationStatusRequest;
import com.smartats.module.application.entity.JobApplication;
import com.smartats.module.application.mapper.JobApplicationMapper;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import com.smartats.module.webhook.service.WebhookService;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * JobApplicationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobApplicationService 单元测试")
class JobApplicationServiceTest {

    @InjectMocks
    private JobApplicationService jobApplicationService;

    @Mock
    private JobApplicationMapper jobApplicationMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private CandidateMapper candidateMapper;
    @Mock
    private WebhookService webhookService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private MatchScoreService matchScoreService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Job publishedJob;
    private Candidate mockCandidate;

    @BeforeEach
    void setUp() {
        publishedJob = new Job();
        publishedJob.setId(1L);
        publishedJob.setTitle("Java 高级工程师");
        publishedJob.setStatus(JobStatus.PUBLISHED.getCode());

        mockCandidate = new Candidate();
        mockCandidate.setId(10L);
        mockCandidate.setName("李四");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 创建申请测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("创建职位申请")
    class CreateApplicationTests {

        @Test
        @DisplayName("正常创建申请 - 初始状态为 PENDING")
        void createApplication_success() {
            // Given
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(1L);
            request.setCandidateId(10L);

            given(jobMapper.selectById(1L)).willReturn(publishedJob);
            given(candidateMapper.selectById(10L)).willReturn(mockCandidate);
            given(jobApplicationMapper.selectCount(any())).willReturn(0L); // 无重复
            given(jobApplicationMapper.insert(any(JobApplication.class))).willAnswer(invocation -> {
                JobApplication app = invocation.getArgument(0);
                app.setId(100L);
                return 1;
            });

            // When
            Long appId = jobApplicationService.createApplication(request);

            // Then
            assertThat(appId).isEqualTo(100L);

            ArgumentCaptor<JobApplication> captor = ArgumentCaptor.forClass(JobApplication.class);
            then(jobApplicationMapper).should().insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.PENDING.getCode());
            assertThat(captor.getValue().getJobId()).isEqualTo(1L);
            assertThat(captor.getValue().getCandidateId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("职位不存在时抛异常")
        void createApplication_jobNotFound() {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(999L);
            request.setCandidateId(10L);

            given(jobMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> jobApplicationService.createApplication(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("职位不存在");
        }

        @Test
        @DisplayName("职位未发布时抛异常")
        void createApplication_jobNotPublished() {
            publishedJob.setStatus(JobStatus.DRAFT.getCode());
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(1L);
            request.setCandidateId(10L);

            given(jobMapper.selectById(1L)).willReturn(publishedJob);

            assertThatThrownBy(() -> jobApplicationService.createApplication(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("职位未发布");
        }

        @Test
        @DisplayName("候选人不存在时抛异常")
        void createApplication_candidateNotFound() {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(1L);
            request.setCandidateId(999L);

            given(jobMapper.selectById(1L)).willReturn(publishedJob);
            given(candidateMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> jobApplicationService.createApplication(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("候选人不存在");
        }

        @Test
        @DisplayName("重复申请时抛异常")
        void createApplication_duplicate() {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(1L);
            request.setCandidateId(10L);

            given(jobMapper.selectById(1L)).willReturn(publishedJob);
            given(candidateMapper.selectById(10L)).willReturn(mockCandidate);
            given(jobApplicationMapper.selectCount(any())).willReturn(1L); // 存在重复

            assertThatThrownBy(() -> jobApplicationService.createApplication(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已申请");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 状态更新测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("更新申请状态")
    class UpdateStatusTests {

        private JobApplication pendingApp;

        @BeforeEach
        void setUp() {
            pendingApp = new JobApplication();
            pendingApp.setId(1L);
            pendingApp.setJobId(10L);
            pendingApp.setCandidateId(20L);
            pendingApp.setStatus(ApplicationStatus.PENDING.getCode());

            lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("合法状态流转：PENDING → SCREENING")
        void updateStatus_pendingToScreening() {
            given(jobApplicationMapper.selectById(1L)).willReturn(pendingApp);
            given(jobApplicationMapper.updateById(any(JobApplication.class))).willReturn(1);

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(ApplicationStatus.SCREENING.getCode());

            jobApplicationService.updateStatus(1L, request);

            ArgumentCaptor<JobApplication> captor = ArgumentCaptor.forClass(JobApplication.class);
            then(jobApplicationMapper).should().updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.SCREENING.getCode());
        }

        @Test
        @DisplayName("合法状态流转：SCREENING → INTERVIEW")
        void updateStatus_screeningToInterview() {
            pendingApp.setStatus(ApplicationStatus.SCREENING.getCode());
            given(jobApplicationMapper.selectById(1L)).willReturn(pendingApp);
            given(jobApplicationMapper.updateById(any(JobApplication.class))).willReturn(1);

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(ApplicationStatus.INTERVIEW.getCode());

            jobApplicationService.updateStatus(1L, request);

            ArgumentCaptor<JobApplication> captor = ArgumentCaptor.forClass(JobApplication.class);
            then(jobApplicationMapper).should().updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.INTERVIEW.getCode());
        }

        @Test
        @DisplayName("非法状态流转：PENDING → OFFER（跳过 SCREENING 和 INTERVIEW）")
        void updateStatus_illegalTransition() {
            given(jobApplicationMapper.selectById(1L)).willReturn(pendingApp);

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(ApplicationStatus.OFFER.getCode());

            assertThatThrownBy(() -> jobApplicationService.updateStatus(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不允许");
        }

        @Test
        @DisplayName("终态不允许变更：REJECTED → 任何状态")
        void updateStatus_rejectedCannotTransition() {
            pendingApp.setStatus(ApplicationStatus.REJECTED.getCode());
            given(jobApplicationMapper.selectById(1L)).willReturn(pendingApp);

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(ApplicationStatus.PENDING.getCode());

            assertThatThrownBy(() -> jobApplicationService.updateStatus(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不允许");
        }

        @Test
        @DisplayName("申请不存在时抛异常")
        void updateStatus_notFound() {
            given(jobApplicationMapper.selectById(999L)).willReturn(null);

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(ApplicationStatus.SCREENING.getCode());

            assertThatThrownBy(() -> jobApplicationService.updateStatus(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("申请记录不存在");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 查询测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("查询申请")
    class QueryTests {

        @BeforeEach
        void setUp() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        @DisplayName("根据 ID 查询 - 缓存未命中时从数据库查询")
        void getById_cacheMiss() throws Exception {
            JobApplication app = new JobApplication();
            app.setId(1L);
            app.setJobId(10L);
            app.setCandidateId(20L);
            app.setStatus(ApplicationStatus.PENDING.getCode());
            app.setAppliedAt(LocalDateTime.now());
            app.setUpdatedAt(LocalDateTime.now());

            given(valueOperations.get(anyString())).willReturn(null);
            given(jobApplicationMapper.selectById(1L)).willReturn(app);
            given(jobMapper.selectById(10L)).willReturn(publishedJob);
            given(candidateMapper.selectById(20L)).willReturn(mockCandidate);
            given(objectMapper.writeValueAsString(any())).willReturn("{}");

            var response = jobApplicationService.getById(1L);

            assertThat(response).isNotNull();
            assertThat(response.getJobTitle()).isEqualTo("Java 高级工程师");
            assertThat(response.getCandidateName()).isEqualTo("李四");
        }

        @Test
        @DisplayName("申请不存在时抛异常")
        void getById_notFound() {
            given(valueOperations.get(anyString())).willReturn(null);
            given(jobApplicationMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> jobApplicationService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("申请记录不存在");
        }

        @Test
        @DisplayName("按职位查询返回分页结果")
        void listByJobId_returnsPaged() {
            Page<JobApplication> emptyPage = new Page<>(1, 10, 0);
            emptyPage.setRecords(java.util.List.of());
            given(jobApplicationMapper.selectPage(any(), any())).willReturn(emptyPage);

            var result = jobApplicationService.listByJobId(1L, 1, 10);

            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isZero();
        }
    }
}
