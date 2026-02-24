package com.smartats.infrastructure.vector;

import com.smartats.module.candidate.entity.Candidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * EmbeddingService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingService 单元测试")
class EmbeddingServiceTest {

    @InjectMocks
    private EmbeddingService embeddingService;

    @Mock
    private EmbeddingModel embeddingModel;

    private Candidate testCandidate;

    @BeforeEach
    void setUp() {
        testCandidate = new Candidate();
        testCandidate.setId(1L);
        testCandidate.setName("张三");
        testCandidate.setGender("MALE");
        testCandidate.setEducation("本科");
        testCandidate.setSchool("北京大学");
        testCandidate.setMajor("计算机科学");
        testCandidate.setWorkYears(5);
        testCandidate.setCurrentCompany("腾讯");
        testCandidate.setCurrentPosition("高级后端工程师");
        testCandidate.setSkills(List.of("Java", "Spring Boot", "MySQL", "Redis"));
        testCandidate.setSelfEvaluation("5年后端开发经验，擅长高并发系统设计");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // buildCandidateText 测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("buildCandidateText")
    class BuildCandidateTextTests {

        @Test
        @DisplayName("包含所有非空字段")
        void shouldIncludeAllNonNullFields() {
            String text = embeddingService.buildCandidateText(testCandidate);

            assertThat(text)
                    .contains("姓名: 张三")
                    .contains("性别: MALE")
                    .contains("学历: 本科")
                    .contains("院校: 北京大学")
                    .contains("专业: 计算机科学")
                    .contains("工作年限: 5年")
                    .contains("当前公司: 腾讯")
                    .contains("当前职位: 高级后端工程师")
                    .contains("技能: Java, Spring Boot, MySQL, Redis")
                    .contains("自我评价: 5年后端开发经验");
        }

        @Test
        @DisplayName("字段为空时跳过")
        void shouldSkipNullFields() {
            Candidate minimal = new Candidate();
            minimal.setName("李四");

            String text = embeddingService.buildCandidateText(minimal);

            assertThat(text).contains("姓名: 李四");
            assertThat(text).doesNotContain("学历:");
            assertThat(text).doesNotContain("技能:");
        }

        @Test
        @DisplayName("包含工作经历信息")
        void shouldIncludeWorkExperience() {
            testCandidate.setWorkExperience(List.of(
                    Map.of("company", "阿里巴巴", "position", "Java 开发", "description", "负责核心交易系统")
            ));

            String text = embeddingService.buildCandidateText(testCandidate);
            assertThat(text)
                    .contains("工作经历:")
                    .contains("阿里巴巴")
                    .contains("Java 开发")
                    .contains("负责核心交易系统");
        }

        @Test
        @DisplayName("包含项目经历信息")
        void shouldIncludeProjectExperience() {
            testCandidate.setProjectExperience(List.of(
                    Map.of("name", "电商平台", "role", "技术负责人", "description", "从0到1搭建微服务架构")
            ));

            String text = embeddingService.buildCandidateText(testCandidate);
            assertThat(text)
                    .contains("项目经历:")
                    .contains("电商平台")
                    .contains("技术负责人")
                    .contains("从0到1搭建微服务架构");
        }

        @Test
        @DisplayName("超长文本截断到最大长度")
        void shouldTruncateLongText() {
            // 构建超长自我评价
            String longText = "A".repeat(10000);
            testCandidate.setSelfEvaluation(longText);

            String text = embeddingService.buildCandidateText(testCandidate);
            assertThat(text.length()).isLessThanOrEqualTo(6000);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // generateCandidateEmbedding 测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("generateCandidateEmbedding")
    class GenerateCandidateEmbeddingTests {

        @Test
        @DisplayName("成功生成候选人嵌入向量")
        void shouldGenerateEmbedding() {
            // Given
            float[] mockEmbedding = new float[1024];
            for (int i = 0; i < 1024; i++) {
                mockEmbedding[i] = (float) Math.random();
            }

            Embedding result = mock(Embedding.class);
            given(result.getOutput()).willReturn(mockEmbedding);

            EmbeddingResponse response = mock(EmbeddingResponse.class);
            given(response.getResult()).willReturn(result);

            given(embeddingModel.call(any(EmbeddingRequest.class))).willReturn(response);

            // When
            List<Float> embedding = embeddingService.generateCandidateEmbedding(testCandidate);

            // Then
            assertThat(embedding).hasSize(1024);
            then(embeddingModel).should().call(any(EmbeddingRequest.class));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // generateQueryEmbedding 测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("generateQueryEmbedding")
    class GenerateQueryEmbeddingTests {

        @Test
        @DisplayName("成功生成查询嵌入向量")
        void shouldGenerateQueryEmbedding() {
            // Given
            float[] mockEmbedding = new float[1024];
            Embedding result = mock(Embedding.class);
            given(result.getOutput()).willReturn(mockEmbedding);

            EmbeddingResponse response = mock(EmbeddingResponse.class);
            given(response.getResult()).willReturn(result);

            given(embeddingModel.call(any(EmbeddingRequest.class))).willReturn(response);

            // When
            List<Float> embedding = embeddingService.generateQueryEmbedding("3年Java后端开发");

            // Then
            assertThat(embedding).hasSize(1024);
        }
    }
}
