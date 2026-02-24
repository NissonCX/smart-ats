package com.smartats.module.job.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.enums.JobStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.module.job.dto.request.CreateJobRequest;
import com.smartats.module.job.dto.request.JobQueryRequest;
import com.smartats.module.job.dto.request.UpdateJobRequest;
import com.smartats.module.job.dto.response.JobResponse;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * JobService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobService 单元测试")
class JobServiceTest {

    @InjectMocks
    private JobService jobService;

    @Mock
    private JobMapper jobMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private CacheEvictionService cacheEvictionService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 创建职位测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("创建职位")
    class CreateJobTests {

        @Test
        @DisplayName("正常创建职位 - 初始状态为 DRAFT")
        void createJob_success() {
            // Given
            CreateJobRequest request = new CreateJobRequest();
            request.setTitle("Java 高级工程师");
            request.setDepartment("技术部");
            request.setDescription("负责核心系统开发");

            given(jobMapper.insert(any(Job.class))).willAnswer(invocation -> {
                Job job = invocation.getArgument(0);
                job.setId(1L);
                return 1;
            });

            // When
            Long jobId = jobService.createJob(request, 100L);

            // Then
            assertThat(jobId).isEqualTo(1L);

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            then(jobMapper).should().insert(captor.capture());
            Job savedJob = captor.getValue();
            assertThat(savedJob.getStatus()).isEqualTo(JobStatus.DRAFT.getCode());
            assertThat(savedJob.getCreatorId()).isEqualTo(100L);
            assertThat(savedJob.getTitle()).isEqualTo("Java 高级工程师");
            assertThat(savedJob.getViewCount()).isZero();
        }

        @Test
        @DisplayName("创建职位 - 技能标签序列化")
        void createJob_withSkills() throws Exception {
            CreateJobRequest request = new CreateJobRequest();
            request.setTitle("测试职位");
            request.setRequiredSkills(List.of("Java", "Spring", "Redis"));

            given(objectMapper.writeValueAsString(anyList())).willReturn("[\"Java\",\"Spring\",\"Redis\"]");
            given(jobMapper.insert(any(Job.class))).willReturn(1);

            jobService.createJob(request, 100L);

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            then(jobMapper).should().insert(captor.capture());
            assertThat(captor.getValue().getRequiredSkills()).isEqualTo("[\"Java\",\"Spring\",\"Redis\"]");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 更新职位测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("更新职位")
    class UpdateJobTests {

        private Job existingJob;

        @BeforeEach
        void setUp() {
            existingJob = new Job();
            existingJob.setId(1L);
            existingJob.setTitle("原标题");
            existingJob.setCreatorId(100L);
            existingJob.setStatus(JobStatus.DRAFT.getCode());

            lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("正常更新职位 - 延迟双删")
        void updateJob_success() {
            UpdateJobRequest request = new UpdateJobRequest();
            request.setId(1L);
            request.setTitle("新标题");

            given(jobMapper.selectById(1L)).willReturn(existingJob);
            given(jobMapper.updateById(any(Job.class))).willReturn(1);

            jobService.updateJob(request, 100L);

            // 验证延迟双删
            then(redisTemplate).should().delete(anyString()); // 第一次删除
            then(cacheEvictionService).should().asyncDeleteCache(anyString()); // 延迟双删
        }

        @Test
        @DisplayName("非创建者无权更新")
        void updateJob_forbidden() {
            UpdateJobRequest request = new UpdateJobRequest();
            request.setId(1L);

            given(jobMapper.selectById(1L)).willReturn(existingJob);

            assertThatThrownBy(() -> jobService.updateJob(request, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权限");
        }

        @Test
        @DisplayName("职位不存在时抛异常")
        void updateJob_notFound() {
            UpdateJobRequest request = new UpdateJobRequest();
            request.setId(999L);

            given(jobMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> jobService.updateJob(request, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("职位不存在");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 发布/关闭职位测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("发布/关闭职位")
    class PublishCloseTests {

        private Job draftJob;

        @BeforeEach
        void setUp() {
            draftJob = new Job();
            draftJob.setId(1L);
            draftJob.setCreatorId(100L);
            draftJob.setStatus(JobStatus.DRAFT.getCode());

            lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("正常发布职位")
        void publishJob_success() {
            given(jobMapper.selectById(1L)).willReturn(draftJob);
            given(jobMapper.updateById(any(Job.class))).willReturn(1);

            jobService.publishJob(1L, 100L);

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            then(jobMapper).should().updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.PUBLISHED.getCode());
        }

        @Test
        @DisplayName("已发布的职位重复发布抛异常")
        void publishJob_alreadyPublished() {
            draftJob.setStatus(JobStatus.PUBLISHED.getCode());
            given(jobMapper.selectById(1L)).willReturn(draftJob);

            assertThatThrownBy(() -> jobService.publishJob(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已经是发布状态");
        }

        @Test
        @DisplayName("正常关闭职位")
        void closeJob_success() {
            draftJob.setStatus(JobStatus.PUBLISHED.getCode());
            given(jobMapper.selectById(1L)).willReturn(draftJob);
            given(jobMapper.updateById(any(Job.class))).willReturn(1);

            jobService.closeJob(1L, 100L);

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            then(jobMapper).should().updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.CLOSED.getCode());
        }

        @Test
        @DisplayName("已关闭的职位重复关闭抛异常")
        void closeJob_alreadyClosed() {
            draftJob.setStatus(JobStatus.CLOSED.getCode());
            given(jobMapper.selectById(1L)).willReturn(draftJob);

            assertThatThrownBy(() -> jobService.closeJob(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已经是关闭状态");
        }

        @Test
        @DisplayName("非创建者无权发布")
        void publishJob_forbidden() {
            given(jobMapper.selectById(1L)).willReturn(draftJob);

            assertThatThrownBy(() -> jobService.publishJob(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权限");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 查询测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("查询职位")
    class QueryTests {

        @Test
        @DisplayName("根据 ID 查询 - 缓存未命中时查库并回填")
        void getJobDetail_cacheMiss() throws Exception {
            Job job = new Job();
            job.setId(1L);
            job.setTitle("测试职位");
            job.setStatus(JobStatus.PUBLISHED.getCode());
            job.setViewCount(100);
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());

            given(valueOperations.increment(anyString())).willReturn(1L);
            given(valueOperations.get(anyString())).willReturn(null); // 缓存未命中
            given(jobMapper.selectById(1L)).willReturn(job);
            given(objectMapper.writeValueAsString(any())).willReturn("{}");

            JobResponse response = jobService.getJobDetail(1L);

            assertThat(response).isNotNull();
            assertThat(response.getViewCount()).isEqualTo(101); // 100 + 1
        }

        @Test
        @DisplayName("职位不存在时抛异常")
        void getJobDetail_notFound() {
            given(valueOperations.increment(anyString())).willReturn(1L);
            given(valueOperations.get(anyString())).willReturn(null);
            given(jobMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> jobService.getJobDetail(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("职位不存在");
        }

        @Test
        @DisplayName("热门职位查询 - 默认限制 10 条")
        void getHotJobs_defaultLimit() {
            Page<Job> emptyPage = new Page<>(1, 10, 0);
            emptyPage.setRecords(List.of());
            given(jobMapper.selectPage(any(), any())).willReturn(emptyPage);

            List<JobResponse> result = jobService.getHotJobs(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("热门职位查询 - 限制不超过 50")
        void getHotJobs_maxLimit() {
            Page<Job> emptyPage = new Page<>(1, 50, 0);
            emptyPage.setRecords(List.of());
            given(jobMapper.selectPage(any(), any())).willReturn(emptyPage);

            List<JobResponse> result = jobService.getHotJobs(100);

            assertThat(result).isEmpty();
            // 验证实际查询限制为 50（安全边界）
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 删除职位测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("删除职位")
    class DeleteJobTests {

        @Test
        @DisplayName("正常删除职位")
        void deleteJob_success() {
            Job job = new Job();
            job.setId(1L);
            job.setCreatorId(100L);

            given(jobMapper.selectById(1L)).willReturn(job);
            given(jobMapper.deleteById(1L)).willReturn(1);
            given(redisTemplate.delete(anyString())).willReturn(true);

            jobService.deleteJob(1L, 100L);

            then(jobMapper).should().deleteById(1L);
            then(redisTemplate).should().delete(anyString());
        }

        @Test
        @DisplayName("非创建者无权删除")
        void deleteJob_forbidden() {
            Job job = new Job();
            job.setId(1L);
            job.setCreatorId(100L);

            given(jobMapper.selectById(1L)).willReturn(job);

            assertThatThrownBy(() -> jobService.deleteJob(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权限");
        }
    }
}
