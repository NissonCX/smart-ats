package com.smartats.infrastructure.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Milvus 向量存储服务
 * <p>
 * 管理 Milvus Collection 的创建、向量插入、相似度搜索、删除。
 * <p>
 * Collection Schema: candidate_vectors
 * - candidate_id (INT64, PK) — 候选人 ID
 * - embedding (FLOAT_VECTOR, dim=1024) — 候选人嵌入向量
 * - candidate_name (VARCHAR, max=200) — 冗余姓名字段（用于结果展示）
 * <p>
 * Index: IVF_FLAT（适合中小规模数据集 < 100万，召回率高）
 * Metric: COSINE（余弦相似度，适合文本语义匹配）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final MilvusClientV2 milvusClient;
    private final Gson gson = new Gson();

    /** Collection 名称 */
    public static final String COLLECTION_NAME = "candidate_vectors";

    /** 向量维度（与 embedding-3 输出一致） */
    private static final int VECTOR_DIMENSION = EmbeddingService.EMBEDDING_DIMENSION;

    /** IVF_FLAT 聚类数（推荐 4 * sqrt(N)，以 10000 条为基准） */
    private static final int NLIST = 128;

    /**
     * 应用启动时初始化 Collection（幂等操作）
     */
    @PostConstruct
    public void initCollection() {
        try {
            boolean exists = milvusClient.hasCollection(
                    HasCollectionReq.builder()
                            .collectionName(COLLECTION_NAME)
                            .build()
            );

            if (exists) {
                log.info("Milvus Collection 已存在: {}", COLLECTION_NAME);
                loadCollection();
                return;
            }

            // 定义 Schema（使用 AddFieldReq）
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .build();

            schema.addField(AddFieldReq.builder()
                    .fieldName("candidate_id")
                    .dataType(DataType.Int64)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName("embedding")
                    .dataType(DataType.FloatVector)
                    .dimension(VECTOR_DIMENSION)
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName("candidate_name")
                    .dataType(DataType.VarChar)
                    .maxLength(200)
                    .build());

            // 定义索引
            IndexParam indexParam = IndexParam.builder()
                    .fieldName("embedding")
                    .indexType(IndexParam.IndexType.IVF_FLAT)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(Map.of("nlist", NLIST))
                    .build();

            // 创建 Collection
            CreateCollectionReq createReq = CreateCollectionReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .collectionSchema(schema)
                    .indexParams(List.of(indexParam))
                    .build();

            milvusClient.createCollection(createReq);
            log.info("Milvus Collection 创建成功: {}", COLLECTION_NAME);

            loadCollection();

        } catch (Exception e) {
            log.error("Milvus Collection 初始化失败: {}", COLLECTION_NAME, e);
            throw new IllegalStateException("Milvus Collection 初始化失败", e);
        }
    }

    /**
     * 插入或更新候选人向量（Upsert 语义）
     *
     * @param candidateId   候选人 ID
     * @param candidateName 候选人姓名
     * @param embedding     1024 维嵌入向量
     * @return Milvus 内部 ID 字符串
     */
    public String upsertVector(Long candidateId, String candidateName, List<Float> embedding) {
        log.info("Upsert 候选人向量: candidateId={}", candidateId);

        JsonObject row = new JsonObject();
        row.addProperty("candidate_id", candidateId);
        row.add("embedding", gson.toJsonTree(embedding));
        row.addProperty("candidate_name", candidateName != null ? candidateName : "");

        UpsertResp resp = milvusClient.upsert(UpsertReq.builder()
                .collectionName(COLLECTION_NAME)
                .data(List.of(row))
                .build());

        log.info("候选人向量 Upsert 成功: candidateId={}, upsertCount={}",
                candidateId, resp.getUpsertCnt());
        return String.valueOf(candidateId);
    }

    /**
     * 批量插入候选人向量
     *
     * @param dataRows 数据行列表（JsonObject），每行包含 candidate_id、embedding、candidate_name
     * @return 插入数量
     */
    public int batchInsert(List<JsonObject> dataRows) {
        if (dataRows == null || dataRows.isEmpty()) {
            return 0;
        }

        InsertResp resp = milvusClient.insert(InsertReq.builder()
                .collectionName(COLLECTION_NAME)
                .data(dataRows)
                .build());

        log.info("批量插入向量完成: count={}", resp.getInsertCnt());
        return (int) resp.getInsertCnt();
    }

    /**
     * 相似度搜索
     *
     * @param queryEmbedding 查询向量（1024 维）
     * @param topK           返回最相似的 K 个结果
     * @return 搜索结果列表（包含 candidateId 和相似度分数）
     */
    public List<SearchResult> search(List<Float> queryEmbedding, int topK) {
        log.info("执行向量搜索: topK={}", topK);

        FloatVec queryVector = new FloatVec(queryEmbedding);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(COLLECTION_NAME)
                .data(Collections.singletonList(queryVector))
                .topK(topK)
                .annsField("embedding")
                .outputFields(List.of("candidate_id", "candidate_name"))
                .build());

        List<SearchResult> results = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

        if (searchResults != null && !searchResults.isEmpty()) {
            for (SearchResp.SearchResult hit : searchResults.get(0)) {
                SearchResult result = new SearchResult();
                result.setCandidateId(((Number) hit.getEntity().get("candidate_id")).longValue());
                result.setCandidateName((String) hit.getEntity().get("candidate_name"));
                result.setScore(hit.getScore());
                results.add(result);
            }
        }

        log.info("向量搜索完成: resultCount={}", results.size());
        return results;
    }

    /**
     * 删除候选人向量
     *
     * @param candidateId 候选人 ID
     */
    public void deleteVector(Long candidateId) {
        log.info("删除候选人向量: candidateId={}", candidateId);

        milvusClient.delete(DeleteReq.builder()
                .collectionName(COLLECTION_NAME)
                .ids(List.of(candidateId))
                .build());

        log.info("候选人向量删除成功: candidateId={}", candidateId);
    }

    /**
     * 加载 Collection 到内存（搜索前必须加载）
     */
    private void loadCollection() {
        milvusClient.loadCollection(LoadCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .build());
        log.info("Milvus Collection 已加载到内存: {}", COLLECTION_NAME);
    }

    /**
     * 向量搜索结果
     */
    @lombok.Data
    public static class SearchResult {
        /** 候选人 ID */
        private Long candidateId;
        /** 候选人姓名（冗余字段） */
        private String candidateName;
        /** 相似度分数（COSINE: 0~1，越接近 1 越相似） */
        private float score;
    }
}
