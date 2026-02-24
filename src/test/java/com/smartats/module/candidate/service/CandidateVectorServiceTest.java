package com.smartats.module.candidate.service;

import com.smartats.infrastructure.vector.EmbeddingService;
import com.smartats.infrastructure.vector.VectorStoreService;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * CandidateVectorService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CandidateVectorService 单元测试")
class CandidateVectorServiceTest {

    @InjectMocks
    private CandidateVectorService candidateVectorService;

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStoreService vectorStoreService;
    @Mock
    private CandidateMapper candidateMapper;

    private Candidate testCandidate;
    private List<Float> mockEmbedding;

    @BeforeEach
    void setUp() {
        testCandidate = new Candidate();
        testCandidate.setId(1L);
        testCandidate.setName("张三");

        mockEmbedding = new ArrayList<>(Collections.nCopies(1024, 0.1f));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // vectorizeCandidate 测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("vectorizeCandidate")
    class VectorizeCandidateTests {

        @Test
        @DisplayName("成功向量化并回写 MySQL")
        void shouldVectorizeAndUpdateMySQL() {
            // Given
            given(embeddingService.buildCandidateText(any(Candidate.class)))
                    .willReturn("姓名: 张三\n学历: 本科");
            given(embeddingService.generateCandidateEmbedding(any(Candidate.class)))
                    .willReturn(mockEmbedding);
            given(vectorStoreService.upsertVector(eq(1L), eq("张三"), anyList()))
                    .willReturn("1");

            // When
            candidateVectorService.vectorizeCandidate(testCandidate);

            // Then
            then(embeddingService).should().buildCandidateText(testCandidate);
            then(embeddingService).should().generateCandidateEmbedding(testCandidate);
            then(vectorStoreService).should().upsertVector(eq(1L), eq("张三"), eq(mockEmbedding));

            // 验证回写 MySQL
            ArgumentCaptor<Candidate> captor = ArgumentCaptor.forClass(Candidate.class);
            then(candidateMapper).should().updateById(captor.capture());

            Candidate updated = captor.getValue();
            assertThat(updated.getId()).isEqualTo(1L);
            assertThat(updated.getVectorId()).isEqualTo("1");
            assertThat(updated.getAiSummary()).isEqualTo("姓名: 张三\n学历: 本科");
            assertThat(updated.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("向量化失败不抛出异常（容错）")
        void shouldNotThrowWhenVectorizationFails() {
            // Given
            given(embeddingService.buildCandidateText(any(Candidate.class)))
                    .willReturn("张三");
            given(embeddingService.generateCandidateEmbedding(any(Candidate.class)))
                    .willThrow(new RuntimeException("嵌入服务不可用"));

            // When & Then — 不抛异常
            assertThatCode(() -> candidateVectorService.vectorizeCandidate(testCandidate))
                    .doesNotThrowAnyException();

            // Milvus 和 MySQL 不应被调用
            then(vectorStoreService).shouldHaveNoInteractions();
            then(candidateMapper).shouldHaveNoInteractions();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // deleteVector 测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("deleteVector")
    class DeleteVectorTests {

        @Test
        @DisplayName("成功删除向量")
        void shouldDeleteVector() {
            candidateVectorService.deleteVector(1L);

            then(vectorStoreService).should().deleteVector(1L);
        }

        @Test
        @DisplayName("删除失败不抛出异常（容错）")
        void shouldNotThrowWhenDeleteFails() {
            willThrow(new RuntimeException("连接失败"))
                    .given(vectorStoreService).deleteVector(anyLong());

            assertThatCode(() -> candidateVectorService.deleteVector(1L))
                    .doesNotThrowAnyException();
        }
    }
}
