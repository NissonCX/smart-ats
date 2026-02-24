package com.smartats.module.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.module.candidate.dto.CandidateQueryRequest;
import com.smartats.module.candidate.dto.CandidateUpdateRequest;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.resume.dto.CandidateInfo;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * CandidateService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CandidateService 单元测试")
class CandidateServiceTest {

    @InjectMocks
    private CandidateService candidateService;

    @Mock
    private CandidateMapper candidateMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private CandidateVectorService candidateVectorService;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private Candidate testCandidate;
    private CandidateInfo testCandidateInfo;

    @BeforeEach
    void setUp() {
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
        testCandidate.setMajor("计算机科学");
        testCandidate.setWorkYears(5);
        testCandidate.setCurrentCompany("阿里巴巴");
        testCandidate.setCurrentPosition("高级工程师");
        testCandidate.setSkills(List.of("Java", "Spring", "MySQL"));
        testCandidate.setCreatedAt(LocalDateTime.now());
        testCandidate.setUpdatedAt(LocalDateTime.now());

        testCandidateInfo = new CandidateInfo();
        testCandidateInfo.setName("张三");
        testCandidateInfo.setPhone("13800138000");
        testCandidateInfo.setEmail("zhangsan@test.com");
        testCandidateInfo.setGender("男");
        testCandidateInfo.setAge(28);
        testCandidateInfo.setEducation("本科");
        testCandidateInfo.setSchool("清华大学");
        testCandidateInfo.setMajor("计算机科学");
        testCandidateInfo.setWorkYears(5);
        testCandidateInfo.setCurrentCompany("阿里巴巴");
        testCandidateInfo.setCurrentPosition("高级工程师");
        testCandidateInfo.setSkills(List.of("Java", "Spring", "MySQL"));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 创建候选人
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("createCandidate")
    class CreateCandidateTests {

        @Test
        @DisplayName("新建候选人成功")
        void shouldCreateNewCandidate() {
            given(candidateMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);
            given(candidateMapper.insert(any(Candidate.class))).willAnswer(invocation -> {
                Candidate c = invocation.getArgument(0);
                c.setId(1L);
                return 1;
            });

            Candidate result = candidateService.createCandidate(100L, testCandidateInfo, "{}");

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("张三");
            assertThat(result.getResumeId()).isEqualTo(100L);
            assertThat(result.getGender()).isEqualTo("MALE"); // normalized
            then(candidateMapper).should().insert(any(Candidate.class));
        }

        @Test
        @DisplayName("候选人已存在则更新")
        void shouldUpdateWhenAlreadyExists() {
            given(candidateMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(testCandidate);
            given(candidateMapper.updateById(any(Candidate.class))).willReturn(1);
            given(candidateMapper.selectById(1L)).willReturn(testCandidate);
            given(redisTemplate.delete(anyString())).willReturn(true);

            Candidate result = candidateService.createCandidate(100L, testCandidateInfo, "{}");

            assertThat(result).isNotNull();
            then(candidateMapper).should().updateById(any(Candidate.class));
            then(candidateMapper).should(never()).insert(any(Candidate.class));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 根据ID查询候选人
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("缓存命中时直接返回")
        void shouldReturnFromCacheWhenHit() throws Exception {
            String cachedJson = "{\"id\":1,\"name\":\"张三\"}";
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(contains("cache:candidate:1"))).willReturn(cachedJson);
            given(objectMapper.readValue(eq(cachedJson), eq(Candidate.class))).willReturn(testCandidate);

            Candidate result = candidateService.getById(1L);

            assertThat(result.getName()).isEqualTo("张三");
            then(candidateMapper).should(never()).selectById(anyLong());
        }

        @Test
        @DisplayName("缓存未命中则查库并回填缓存")
        void shouldQueryDbAndFillCacheWhenMiss() throws Exception {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);
            given(candidateMapper.selectById(1L)).willReturn(testCandidate);
            given(objectMapper.writeValueAsString(testCandidate)).willReturn("{}");

            Candidate result = candidateService.getById(1L);

            assertThat(result.getName()).isEqualTo("张三");
            then(candidateMapper).should().selectById(1L);
            then(valueOperations).should().set(anyString(), eq("{}"), eq(30L), any());
        }

        @Test
        @DisplayName("数据库也不存在返回null")
        void shouldReturnNullWhenNotExist() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);
            given(candidateMapper.selectById(999L)).willReturn(null);

            Candidate result = candidateService.getById(999L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("缓存反序列化失败时优雅降级查库")
        void shouldFallbackToDbWhenCacheDeserializationFails() throws Exception {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn("invalid-json");
            given(objectMapper.readValue(eq("invalid-json"), eq(Candidate.class)))
                    .willThrow(new JsonProcessingException("parse error") {});
            given(candidateMapper.selectById(1L)).willReturn(testCandidate);
            given(objectMapper.writeValueAsString(testCandidate)).willReturn("{}");

            Candidate result = candidateService.getById(1L);

            assertThat(result.getName()).isEqualTo("张三");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 手动更新候选人 (updateManual)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("updateManual")
    class UpdateManualTests {

        @Test
        @DisplayName("部分更新成功")
        void shouldPartialUpdateSuccessfully() throws Exception {
            CandidateUpdateRequest request = new CandidateUpdateRequest();
            request.setName("李四");
            request.setPhone("13900139000");
            // other fields null → not updated

            given(candidateMapper.selectById(1L)).willReturn(testCandidate);
            given(candidateMapper.updateById(any(Candidate.class))).willReturn(1);
            given(redisTemplate.delete(anyString())).willReturn(true);
            // For getById after update
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);
            Candidate updated = new Candidate();
            updated.setId(1L);
            updated.setName("李四");
            updated.setPhone("13900139000");
            given(candidateMapper.selectById(1L)).willReturn(updated);
            given(objectMapper.writeValueAsString(any(Candidate.class))).willReturn("{}");

            Candidate result = candidateService.updateManual(1L, request);

            assertThat(result.getName()).isEqualTo("李四");
            then(candidateVectorService).should().vectorizeCandidateAsync(any(Candidate.class));
        }

        @Test
        @DisplayName("候选人不存在抛异常")
        void shouldThrowWhenNotFound() {
            CandidateUpdateRequest request = new CandidateUpdateRequest();
            request.setName("李四");
            given(candidateMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> candidateService.updateManual(999L, request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 删除候选人
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("deleteById")
    class DeleteByIdTests {

        @Test
        @DisplayName("删除成功同时清缓存和向量")
        void shouldDeleteAndEvictCacheAndVector() {
            given(candidateMapper.deleteById(1L)).willReturn(1);
            given(redisTemplate.delete(anyString())).willReturn(true);

            candidateService.deleteById(1L);

            then(candidateMapper).should().deleteById(1L);
            then(redisTemplate).should().delete(contains("cache:candidate:1"));
            then(candidateVectorService).should().deleteVector(1L);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 分页查询测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("listCandidates")
    class ListCandidatesTests {

        @Test
        @DisplayName("无筛选条件分页查询")
        void shouldListWithoutFilters() {
            CandidateQueryRequest request = new CandidateQueryRequest();
            request.setPage(1);
            request.setPageSize(10);

            Page<Candidate> page = new Page<>(1, 10);
            page.setTotal(1);
            page.setRecords(List.of(testCandidate));

            given(candidateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .willReturn(page);

            Page<Candidate> result = candidateService.listCandidates(request);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("关键字筛选查询")
        void shouldListWithKeywordFilter() {
            CandidateQueryRequest request = new CandidateQueryRequest();
            request.setPage(1);
            request.setPageSize(10);
            request.setKeyword("张三");

            Page<Candidate> page = new Page<>(1, 10);
            page.setTotal(1);
            page.setRecords(List.of(testCandidate));

            given(candidateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .willReturn(page);

            Page<Candidate> result = candidateService.listCandidates(request);

            assertThat(result.getRecords()).hasSize(1);
        }

        @Test
        @DisplayName("pageSize限制在1到100")
        void shouldClampPageSize() {
            CandidateQueryRequest request = new CandidateQueryRequest();
            request.setPage(1);
            request.setPageSize(200); // > 100

            Page<Candidate> page = new Page<>(1, 100);
            page.setTotal(0);
            page.setRecords(List.of());

            given(candidateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .willReturn(page);

            candidateService.listCandidates(request);

            then(candidateMapper).should().selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 根据resumeId查询
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("getByResumeId")
    class GetByResumeIdTests {

        @Test
        @DisplayName("存在时返回候选人")
        void shouldReturnCandidate() {
            given(candidateMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(testCandidate);

            Candidate result = candidateService.getByResumeId(100L);

            assertThat(result.getName()).isEqualTo("张三");
        }

        @Test
        @DisplayName("不存在时返回null")
        void shouldReturnNullWhenNotExist() {
            given(candidateMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);

            Candidate result = candidateService.getByResumeId(999L);

            assertThat(result).isNull();
        }
    }
}
