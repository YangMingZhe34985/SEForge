package com.ustb.smartse.modules.knowledgebase.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EmbeddingStoreConfig {
    
    private static final Logger log = LoggerFactory.getLogger(EmbeddingStoreConfig.class);
    
    private final VectorStoreProperties properties;
    
    public EmbeddingStoreConfig(VectorStoreProperties properties) {
        this.properties = properties;
    }

    @Bean
    @Primary
    public EmbeddingStore<TextSegment> embeddingStore(@Qualifier("milvusEmbeddingStore") EmbeddingStore<TextSegment> milvusStore) {
        try {
            log.info("初始化向量存储...");
            return milvusStore;
        } catch (Exception e) {
            log.error("初始化Milvus向量存储失败，回退到内存存储: {}", e.getMessage(), e);
            return new InMemoryEmbeddingStore<>();
        }
    }
} 