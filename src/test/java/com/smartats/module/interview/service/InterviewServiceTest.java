package com.smartats.module.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.enums.ApplicationStatus;
import com.smartats.common.enums.InterviewStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.module.application.entity.JobApplication;
import com.smartats.module.application.mapper.JobApplicationMapper;
import com.smartats.module.auth.entity.User;
import com.smartats.module.auth.mapper.UserMapper;
import com.smartats.module.interview.dto.InterviewResponse;
import com.smartats.module.interview.dto.ScheduleInterviewRequest;
import com.smartats.module.interview.dto.SubmitFeedbackRequest;
import com.smartats.module.interview.entity.InterviewRecord;
import com.smartats.module.interview.mapper.InterviewRecordMapper;
import com.smartats.module.webhook.service.WebhookService;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * InterviewService 单元测试
 * <p>
 * 使用 Mockito 隔离外部依赖，测试核心业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewService 单元测试")
class InterviewServiceTest {

    @InjectMocks
    private InterviewService interviewService;

    @Mock
    private InterviewRecordMapper interviewRecordMapper;
    @Mock
    private JobApplicationMapper jobApplicationMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private WebhookService webhookService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;

    private JobApplication mockApplication;
    private User mockInterviewer;
    private ScheduleInterviewRequest scheduleRequest;

    @BeforeEach
    void setUp() {
        // 构造通用测试数据
        mockApplication = new JobApplication();
        mockApplication.setId(1L);
        mockApplication.setJobId(10L);
        mockApplication.setCandidateId(20L);
        mockApplication.setStatus(ApplicationStatus.SCREENING.getCode());

        mockInterviewer = new User();
        mockInterviewer.setId(100L);
        mockInterviewer.setUsername("面试官张三");

        scheduleRequest = new ScheduleInterviewRequest();
        scheduleRequest.setApplicationId(1L);
        scheduleRequest.setInterviewerId(100L);
        scheduleRequest.setInterviewType("VIDEO");
        scheduleRequest.setScheduledAt(LocalDateTime.now().plusDays(1));
        scheduleRequest.setDurationMinutes(60);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 安排面试测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("安排面试")
    class ScheduleInterviewTests {

        @Test
        @DisplayName("正常安排面试 - 自动递增轮次")
        void scheduleInterview_success_autoRound() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(mockInterviewer);
            given(interviewRecordMapper.selectCount(any())).willReturn(0L); // 无已有轮次
            given(interviewRecordMapper.insert(any(InterviewRecord.class))).willAnswer(invocation -> {
                InterviewRecord record = invocation.getArgument(0);
                record.setId(999L); // 模拟自增 ID
                return 1;
            });

            // When
            Long interviewId = interviewService.scheduleInterview(scheduleRequest);

            // Then
            assertThat(interviewId).isEqualTo(999L);

            ArgumentCaptor<InterviewRecord> captor = ArgumentCaptor.forClass(InterviewRecord.class);
            then(interviewRecordMapper).should().insert(captor.capture());

            InterviewRecord saved = captor.getValue();
            assertThat(saved.getApplicationId()).isEqualTo(1L);
            assertThat(saved.getInterviewerId()).isEqualTo(100L);
            assertThat(saved.getRound()).isEqualTo(1);
            assertThat(saved.getStatus()).isEqualTo(InterviewStatus.SCHEDULED.getCode());
            assertThat(saved.getInterviewType()).isEqualTo("VIDEO");
        }

        @Test
        @DisplayName("安排面试 - 申请状态从 SCREENING 自动推进为 INTERVIEW")
        void scheduleInterview_autoPromoteStatus() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(mockInterviewer);
            given(interviewRecordMapper.selectCount(any())).willReturn(0L);
            given(interviewRecordMapper.insert(any(InterviewRecord.class))).willReturn(1);

            // When
            interviewService.scheduleInterview(scheduleRequest);

            // Then - 验证申请状态被更新为 INTERVIEW
            ArgumentCaptor<JobApplication> appCaptor = ArgumentCaptor.forClass(JobApplication.class);
            then(jobApplicationMapper).should().updateById(appCaptor.capture());
            assertThat(appCaptor.getValue().getStatus()).isEqualTo(ApplicationStatus.INTERVIEW.getCode());
        }

        @Test
        @DisplayName("安排面试 - 申请已经是 INTERVIEW 状态时不再推进")
        void scheduleInterview_noPromoteWhenAlreadyInterview() {
            // Given
            mockApplication.setStatus(ApplicationStatus.INTERVIEW.getCode());
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(mockInterviewer);
            // 第一次 selectCount = 1L (轮次计算)，第二次 = 0L (无时间冲突)
            given(interviewRecordMapper.selectCount(any()))
                    .willReturn(1L)   // 已有面试轮次
                    .willReturn(0L);  // 无时间冲突
            given(interviewRecordMapper.insert(any(InterviewRecord.class))).willReturn(1);

            // When
            interviewService.scheduleInterview(scheduleRequest);

            // Then - 不应再次更新申请状态
            then(jobApplicationMapper).should(never()).updateById(any(JobApplication.class));
        }

        @Test
        @DisplayName("安排面试 - 申请不存在时抛异常")
        void scheduleInterview_applicationNotFound() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(null);

            // When & Then
            assertThatThrownBy(() -> interviewService.scheduleInterview(scheduleRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("申请记录不存在");
        }

        @Test
        @DisplayName("安排面试 - 申请状态不允许安排面试时抛异常")
        void scheduleInterview_invalidStatus() {
            // Given
            mockApplication.setStatus(ApplicationStatus.PENDING.getCode());
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);

            // When & Then
            assertThatThrownBy(() -> interviewService.scheduleInterview(scheduleRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不允许安排面试");
        }

        @Test
        @DisplayName("安排面试 - 面试官不存在时抛异常")
        void scheduleInterview_interviewerNotFound() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(null);

            // When & Then
            assertThatThrownBy(() -> interviewService.scheduleInterview(scheduleRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("面试官不存在");
        }

        @Test
        @DisplayName("安排面试 - 时间冲突时抛异常")
        void scheduleInterview_timeConflict() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(mockInterviewer);
            // 第一次 selectCount 返回已有面试数(轮次计算)，第二次返回冲突数
            given(interviewRecordMapper.selectCount(any()))
                    .willReturn(0L)  // 轮次计算
                    .willReturn(1L); // 冲突检测

            // When & Then
            assertThatThrownBy(() -> interviewService.scheduleInterview(scheduleRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("面试时间冲突");
        }

        @Test
        @DisplayName("安排面试 - 触发 Webhook 事件")
        void scheduleInterview_triggerWebhook() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(mockInterviewer);
            given(interviewRecordMapper.selectCount(any())).willReturn(0L);
            given(interviewRecordMapper.insert(any(InterviewRecord.class))).willReturn(1);

            // When
            interviewService.scheduleInterview(scheduleRequest);

            // Then
            then(webhookService).should().sendEvent(any(), any());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 提交反馈测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("提交面试反馈")
    class SubmitFeedbackTests {

        private InterviewRecord existingRecord;
        private SubmitFeedbackRequest feedbackRequest;

        @BeforeEach
        void setUp() {
            existingRecord = new InterviewRecord();
            existingRecord.setId(1L);
            existingRecord.setApplicationId(10L);
            existingRecord.setRound(1);
            existingRecord.setStatus(InterviewStatus.SCHEDULED.getCode());

            feedbackRequest = new SubmitFeedbackRequest();
            feedbackRequest.setScore(8);
            feedbackRequest.setFeedback("技术能力优秀，沟通表达清晰");
            feedbackRequest.setRecommendation("STRONG_YES");

            // 模拟 Redis 操作
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("正常提交反馈 - 面试状态变为 COMPLETED")
        void submitFeedback_success() {
            // Given
            given(interviewRecordMapper.selectById(1L)).willReturn(existingRecord);
            given(interviewRecordMapper.updateById(any(InterviewRecord.class))).willReturn(1);

            // When
            interviewService.submitFeedback(1L, feedbackRequest);

            // Then
            ArgumentCaptor<InterviewRecord> captor = ArgumentCaptor.forClass(InterviewRecord.class);
            then(interviewRecordMapper).should().updateById(captor.capture());

            InterviewRecord updated = captor.getValue();
            assertThat(updated.getStatus()).isEqualTo(InterviewStatus.COMPLETED.getCode());
            assertThat(updated.getScore()).isEqualTo(8);
            assertThat(updated.getFeedback()).isEqualTo("技术能力优秀，沟通表达清晰");
            assertThat(updated.getRecommendation()).isEqualTo("STRONG_YES");
        }

        @Test
        @DisplayName("面试不存在时抛异常")
        void submitFeedback_notFound() {
            given(interviewRecordMapper.selectById(1L)).willReturn(null);

            assertThatThrownBy(() -> interviewService.submitFeedback(1L, feedbackRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("面试记录不存在");
        }

        @Test
        @DisplayName("已取消的面试不允许提交反馈")
        void submitFeedback_cancelledInterview() {
            existingRecord.setStatus(InterviewStatus.CANCELLED.getCode());
            given(interviewRecordMapper.selectById(1L)).willReturn(existingRecord);

            assertThatThrownBy(() -> interviewService.submitFeedback(1L, feedbackRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("面试已取消");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 取消面试测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("取消面试")
    class CancelInterviewTests {

        private InterviewRecord scheduledRecord;

        @BeforeEach
        void setUp() {
            scheduledRecord = new InterviewRecord();
            scheduledRecord.setId(1L);
            scheduledRecord.setApplicationId(10L);
            scheduledRecord.setRound(1);
            scheduledRecord.setStatus(InterviewStatus.SCHEDULED.getCode());

            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("正常取消面试")
        void cancelInterview_success() {
            given(interviewRecordMapper.selectById(1L)).willReturn(scheduledRecord);
            given(interviewRecordMapper.updateById(any(InterviewRecord.class))).willReturn(1);

            interviewService.cancelInterview(1L);

            ArgumentCaptor<InterviewRecord> captor = ArgumentCaptor.forClass(InterviewRecord.class);
            then(interviewRecordMapper).should().updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(InterviewStatus.CANCELLED.getCode());
        }

        @Test
        @DisplayName("已完成的面试不允许取消")
        void cancelInterview_alreadyCompleted() {
            scheduledRecord.setStatus(InterviewStatus.COMPLETED.getCode());
            given(interviewRecordMapper.selectById(1L)).willReturn(scheduledRecord);

            assertThatThrownBy(() -> interviewService.cancelInterview(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("面试已完成");
        }

        @Test
        @DisplayName("已取消的面试不允许再次取消")
        void cancelInterview_alreadyCancelled() {
            scheduledRecord.setStatus(InterviewStatus.CANCELLED.getCode());
            given(interviewRecordMapper.selectById(1L)).willReturn(scheduledRecord);

            assertThatThrownBy(() -> interviewService.cancelInterview(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("面试已取消");
        }

        @Test
        @DisplayName("面试不存在时抛异常")
        void cancelInterview_notFound() {
            given(interviewRecordMapper.selectById(1L)).willReturn(null);

            assertThatThrownBy(() -> interviewService.cancelInterview(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("面试记录不存在");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 查询测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("查询面试记录")
    class QueryTests {

        @BeforeEach
        void setUp() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        @DisplayName("根据 ID 查询 - 缓存未命中时从数据库查询并回填缓存")
        void getById_cacheMiss() throws Exception {
            // Given
            InterviewRecord record = buildFullRecord();
            User interviewer = mockInterviewer;

            given(valueOperations.get(anyString())).willReturn(null); // 缓存未命中
            given(interviewRecordMapper.selectById(1L)).willReturn(record);
            given(userMapper.selectById(100L)).willReturn(interviewer);
            given(objectMapper.writeValueAsString(any())).willReturn("{}");

            // When
            InterviewResponse response = interviewService.getById(1L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getInterviewerName()).isEqualTo("面试官张三");
            then(valueOperations).should().set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("根据 ID 查询 - 缓存命中时直接返回")
        void getById_cacheHit() throws Exception {
            // Given
            InterviewResponse cached = new InterviewResponse();
            cached.setId(1L);
            cached.setInterviewerName("面试官张三");

            given(valueOperations.get(anyString())).willReturn("{\"id\":1}");
            given(objectMapper.readValue(anyString(), eq(InterviewResponse.class))).willReturn(cached);

            // When
            InterviewResponse response = interviewService.getById(1L);

            // Then
            assertThat(response.getId()).isEqualTo(1L);
            then(interviewRecordMapper).should(never()).selectById(any()); // 不查数据库
        }

        @Test
        @DisplayName("根据 ID 查询 - 面试不存在时抛异常")
        void getById_notFound() {
            given(valueOperations.get(anyString())).willReturn(null);
            given(interviewRecordMapper.selectById(1L)).willReturn(null);

            assertThatThrownBy(() -> interviewService.getById(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("面试记录不存在");
        }

        @Test
        @DisplayName("按申请查询面试列表 - 空列表")
        void listByApplicationId_empty() {
            given(interviewRecordMapper.selectList(any())).willReturn(Collections.emptyList());

            List<InterviewResponse> result = interviewService.listByApplicationId(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("按申请查询面试列表 - 批量查询面试官避免 N+1")
        void listByApplicationId_batchQuery() {
            // Given
            InterviewRecord r1 = buildRecord(1L, 100L, 1, InterviewStatus.COMPLETED.getCode());
            InterviewRecord r2 = buildRecord(2L, 200L, 2, InterviewStatus.SCHEDULED.getCode());

            User u1 = buildUser(100L, "面试官A");
            User u2 = buildUser(200L, "面试官B");

            given(interviewRecordMapper.selectList(any())).willReturn(List.of(r1, r2));
            given(userMapper.selectBatchIds(anyCollection())).willReturn(List.of(u1, u2));

            // When
            List<InterviewResponse> result = interviewService.listByApplicationId(1L);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getInterviewerName()).isEqualTo("面试官A");
            assertThat(result.get(1).getInterviewerName()).isEqualTo("面试官B");

            // 验证只调用了一次批量查询（而非 N 次单条查询）
            then(userMapper).should(times(1)).selectBatchIds(anyCollection());
            then(userMapper).should(never()).selectById(any());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 测试工具方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private InterviewRecord buildFullRecord() {
        InterviewRecord record = new InterviewRecord();
        record.setId(1L);
        record.setApplicationId(10L);
        record.setInterviewerId(100L);
        record.setRound(1);
        record.setInterviewType("VIDEO");
        record.setScheduledAt(LocalDateTime.now().plusDays(1));
        record.setDurationMinutes(60);
        record.setStatus(InterviewStatus.SCHEDULED.getCode());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private InterviewRecord buildRecord(Long id, Long interviewerId, int round, String status) {
        InterviewRecord record = new InterviewRecord();
        record.setId(id);
        record.setApplicationId(10L);
        record.setInterviewerId(interviewerId);
        record.setRound(round);
        record.setInterviewType("VIDEO");
        record.setScheduledAt(LocalDateTime.now().plusDays(1));
        record.setDurationMinutes(60);
        record.setStatus(status);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private User buildUser(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setUsername(name);
        return user;
    }
}
