package com.smartats.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 * <p>
 * Milvus Standalone 通过 gRPC 连接，默认端口 19530。
 * 使用 MilvusClientV2（SDK v2 API），对应 Milvus Server 2.4.x。
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.database:default}")
    private String database;

    @Value("${milvus.connect-timeout:10000}")
    private long connectTimeout;

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient() {
        String uri = "http://" + host + ":" + port;
        log.info("初始化 Milvus 客户端: uri={}, database={}", uri, database);

        ConnectConfig config = ConnectConfig.builder()
                .uri(uri)
                .dbName(database)
                .connectTimeoutMs(connectTimeout)
                .build();

        MilvusClientV2 client = new MilvusClientV2(config);
        log.info("Milvus 客户端初始化成功");
        return client;
    }
}
