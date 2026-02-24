package com.smartats.module.candidate.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.infrastructure.vector.EmbeddingService;
import com.smartats.infrastructure.vector.VectorStoreService;
import com.smartats.infrastructure.vector.VectorStoreService.SearchResult;
import com.smartats.module.candidate.dto.SmartSearchRequest;
import com.smartats.module.candidate.dto.SmartSearchResponse;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * SmartSearchService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmartSearchService 单元测试")
class SmartSearchServiceTest {

    @InjectMocks
    private SmartSearchService smartSearchService;

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStoreService vectorStoreService;
    @Mock
    private CandidateMapper candidateMapper;

    private SmartSearchRequest request;
    private List<Float> mockEmbedding;

    @BeforeEach
    void setUp() {
        request = new SmartSearchRequest();
        request.setQuery("3年以上Java后端开发");
        request.setTopK(10);
        request.setMinScore(0.3);

        mockEmbedding = new ArrayList<>(Collections.nCopies(1024, 0.1f));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 正常搜索流程
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("正常搜索流程")
    class NormalSearchTests {

        @Test
        @DisplayName("成功返回匹配候选人")
        void shouldReturnMatchedCandidates() {
            // Given
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);

            SearchResult sr1 = new SearchResult();
            sr1.setCandidateId(1L);
            sr1.setCandidateName("张三");
            sr1.setScore(0.85f);

            SearchResult sr2 = new SearchResult();
            sr2.setCandidateId(2L);
            sr2.setCandidateName("李四");
            sr2.setScore(0.72f);

            given(vectorStoreService.search(anyList(), eq(10))).willReturn(List.of(sr1, sr2));

            Candidate c1 = buildCandidate(1L, "张三", "高级后端工程师", "腾讯", 5);
            Candidate c2 = buildCandidate(2L, "李四", "后端工程师", "阿里巴巴", 3);
            given(candidateMapper.selectBatchIds(anyList())).willReturn(List.of(c1, c2));

            // When
            SmartSearchResponse response = smartSearchService.search(request);

            // Then
            assertThat(response.getQuery()).isEqualTo("3年以上Java后端开发");
            assertThat(response.getTotalMatches()).isEqualTo(2);
            assertThat(response.getCandidates()).hasSize(2);
            assertThat(response.getCandidates().get(0).getCandidateId()).isEqualTo(1L);
            assertThat(response.getCandidates().get(0).getMatchScore()).isGreaterThan(0.8);
            assertThat(response.getCandidates().get(1).getCandidateId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("按相似度阈值过滤低分结果")
        void shouldFilterByMinScore() {
            // Given
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);

            SearchResult sr1 = new SearchResult();
            sr1.setCandidateId(1L);
            sr1.setCandidateName("张三");
            sr1.setScore(0.85f);

            SearchResult sr2 = new SearchResult();
            sr2.setCandidateId(2L);
            sr2.setCandidateName("李四");
            sr2.setScore(0.15f);  // 低于阈值

            given(vectorStoreService.search(anyList(), eq(10))).willReturn(List.of(sr1, sr2));

            Candidate c1 = buildCandidate(1L, "张三", "后端", "腾讯", 5);
            given(candidateMapper.selectBatchIds(anyList())).willReturn(List.of(c1));

            // When
            SmartSearchResponse response = smartSearchService.search(request);

            // Then
            assertThat(response.getTotalMatches()).isEqualTo(1);
            assertThat(response.getCandidates()).hasSize(1);
            assertThat(response.getCandidates().get(0).getCandidateId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("候选人不在MySQL中时跳过")
        void shouldSkipMissingCandidates() {
            // Given
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);

            SearchResult sr1 = new SearchResult();
            sr1.setCandidateId(1L);
            sr1.setCandidateName("张三");
            sr1.setScore(0.85f);

            SearchResult sr2 = new SearchResult();
            sr2.setCandidateId(999L);
            sr2.setCandidateName("已删除");
            sr2.setScore(0.75f);

            given(vectorStoreService.search(anyList(), eq(10))).willReturn(List.of(sr1, sr2));

            // 只返回 candidateId=1，不包含 999
            Candidate c1 = buildCandidate(1L, "张三", "后端", "腾讯", 5);
            given(candidateMapper.selectBatchIds(anyList())).willReturn(List.of(c1));

            // When
            SmartSearchResponse response = smartSearchService.search(request);

            // Then
            assertThat(response.getTotalMatches()).isEqualTo(1);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 空结果场景
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("空结果场景")
    class EmptyResultTests {

        @Test
        @DisplayName("Milvus 无匹配结果")
        void shouldReturnEmptyWhenNoMilvusResults() {
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);
            given(vectorStoreService.search(anyList(), eq(10))).willReturn(List.of());

            SmartSearchResponse response = smartSearchService.search(request);

            assertThat(response.getTotalMatches()).isZero();
            assertThat(response.getCandidates()).isEmpty();
        }

        @Test
        @DisplayName("所有结果低于阈值")
        void shouldReturnEmptyWhenAllBelowThreshold() {
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);

            SearchResult sr = new SearchResult();
            sr.setCandidateId(1L);
            sr.setCandidateName("张三");
            sr.setScore(0.1f);  // 低于默认 0.3

            given(vectorStoreService.search(anyList(), eq(10))).willReturn(List.of(sr));

            SmartSearchResponse response = smartSearchService.search(request);

            assertThat(response.getTotalMatches()).isZero();
            assertThat(response.getCandidates()).isEmpty();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 异常场景
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("异常场景")
    class ExceptionTests {

        @Test
        @DisplayName("嵌入服务异常抛出 BusinessException")
        void shouldThrowWhenEmbeddingFails() {
            given(embeddingService.generateQueryEmbedding(anyString()))
                    .willThrow(new RuntimeException("API 超时"));

            assertThatThrownBy(() -> smartSearchService.search(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("AI 向量化服务不可用");
        }

        @Test
        @DisplayName("Milvus 搜索异常抛出 BusinessException")
        void shouldThrowWhenMilvusSearchFails() {
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);
            given(vectorStoreService.search(anyList(), eq(10)))
                    .willThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> smartSearchService.search(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("向量搜索服务不可用");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Helper
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private Candidate buildCandidate(Long id, String name, String position, String company, int workYears) {
        Candidate c = new Candidate();
        c.setId(id);
        c.setName(name);
        c.setCurrentPosition(position);
        c.setCurrentCompany(company);
        c.setWorkYears(workYears);
        c.setSkills(List.of("Java", "Spring Boot"));
        return c;
    }
}
