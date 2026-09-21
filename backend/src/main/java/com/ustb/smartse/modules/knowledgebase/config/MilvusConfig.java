package com.ustb.smartse.modules.knowledgebase.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.ShowCollectionsParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;

import java.net.Socket;
import java.io.IOException;
import java.util.List;

/**
 * 向量数据库配置
 */
@Configuration
public class MilvusConfig {
    
    private static final Logger log = LoggerFactory.getLogger(MilvusConfig.class);
    
    private final VectorStoreProperties properties;
    
    public MilvusConfig(VectorStoreProperties properties) {
        this.properties = properties;
    }

    /**
     * 检查Milvus连接是否可用
     */
    private boolean isMilvusAvailable() {
        String host = properties.getMilvus().getHost();
        int port = properties.getMilvus().getPort();
        
        log.info("检查Milvus连接: {}:{}", host, port);
        
        // 先检查网络连接
        try (Socket socket = new Socket(host, port)) {
            log.info("Milvus服务网络连接正常");
            
            // 尝试建立Milvus客户端连接
            try {
                ConnectParam.Builder builder = ConnectParam.newBuilder()
                        .withHost(host)
                        .withPort(port);
                
                // 如果配置了用户名和密码，添加认证信息
                if (properties.getMilvus().getUsername() != null && 
                    !properties.getMilvus().getUsername().isEmpty()) {
                    log.info("使用认证信息连接Milvus");
                    builder.withAuthorization(
                        properties.getMilvus().getUsername(),
                        properties.getMilvus().getPassword()
                    );
                }
                
                ConnectParam connectParam = builder.build();
                log.debug("创建Milvus客户端连接...");
                MilvusServiceClient client = new MilvusServiceClient(connectParam);
                
                // 检查服务器状态
                log.debug("检查Milvus健康状态...");
                R<?> response = client.checkHealth();
                if (response.getStatus() != 0) {
                    log.error("Milvus健康检查失败: 状态码={}, 消息={}", 
                            response.getStatus(), response.getMessage());
                    client.close();
                    return false;
                }
                
                // 检查集合是否存在
                String collectionName = properties.getMilvus().getCollectionPrefix() + "embeddings";
                log.info("检查集合是否存在: {}", collectionName);
                R<Boolean> hasCollection = client.hasCollection(
                    HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
                );
                
                if (hasCollection.getStatus() == 0) {
                    boolean exists = hasCollection.getData();
                    log.info("集合 {} {}", collectionName, exists ? "存在" : "不存在");
                    
                    // 如果集合不存在，列出所有可用的集合
                    if (!exists) {
                        log.info("列出所有可用的集合:");
                        try {
                            // 尝试获取所有集合
                            R<?> showCollections = client.showCollections(
                                ShowCollectionsParam.newBuilder().build()
                            );
                            
                            if (showCollections.getStatus() == 0) {
                                // 根据返回类型进行适当处理
                                Object data = showCollections.getData();
                                log.info("可用集合信息: {}", data != null ? data.toString() : "无数据");
                            } else {
                                log.error("获取集合列表失败: 状态码={}, 消息={}", 
                                        showCollections.getStatus(), showCollections.getMessage());
                            }
                        } catch (Exception e) {
                            log.error("获取集合列表异常: {}", e.getMessage());
                        }
                    }
                } else {
                    log.error("检查集合失败: 状态码={}, 消息={}", 
                            hasCollection.getStatus(), hasCollection.getMessage());
                }
                
                client.close();
                log.info("Milvus连接测试成功");
                return true;
            } catch (Exception e) {
                log.error("Milvus服务连接测试失败: {}", e.getMessage(), e);
                if (e.getCause() != null) {
                    log.error("根本原因: {}", e.getCause().getMessage());
                }
                return false;
            }
        } catch (IOException e) {
            log.error("无法连接到Milvus服务器 {}:{}: {}", host, port, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 创建Milvus嵌入存储
     */
    @Bean(name = "milvusEmbeddingStore")
    @ConditionalOnProperty(name = "smartse.vector-store.milvus.enabled", havingValue = "true", matchIfMissing = true)
    public EmbeddingStore<TextSegment> milvusEmbeddingStore() {
        try {
            log.info("正在连接Milvus向量数据库: {}:{}, 集合前缀: {}, 维度: {}", 
                    properties.getMilvus().getHost(), 
                    properties.getMilvus().getPort(),
                    properties.getMilvus().getCollectionPrefix(),
                    properties.getMilvus().getEmbeddingDimension());
            
            // 检查Milvus是否可达
            if (!isMilvusAvailable()) {
                log.error("Milvus服务不可用，无法创建向量存储");
                throw new RuntimeException("Milvus服务不可用");
            }
            
            // 构建Milvus客户端配置
            log.info("创建MilvusEmbeddingStore...");
            MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder()
                    .host(properties.getMilvus().getHost())
                    .port(properties.getMilvus().getPort())
                    .collectionName(properties.getMilvus().getCollectionPrefix() + "embeddings")
                    .dimension(properties.getMilvus().getEmbeddingDimension());
            
            // 如果配置了用户名和密码，添加认证信息
            if (properties.getMilvus().getUsername() != null && 
                !properties.getMilvus().getUsername().isEmpty()) {
                log.info("添加Milvus认证信息");
                builder.username(properties.getMilvus().getUsername())
                       .password(properties.getMilvus().getPassword());
            }
            
            // 记录自动加载集合设置
            if (properties.getMilvus().isAutoLoadCollection()) {
                log.info("自动加载集合设置为: {}", properties.getMilvus().isAutoLoadCollection());
            }
            
            // 创建并返回MilvusEmbeddingStore实例
            MilvusEmbeddingStore store = builder.build();
            log.info("MilvusEmbeddingStore创建成功");
            return store;
                    
        } catch (Exception e) {
            log.error("连接Milvus失败: {}", e.getMessage(), e);
            log.error("异常类型: {}", e.getClass().getName());
            
            if (e.getCause() != null) {
                log.error("根本原因: {}", e.getCause().getMessage());
            }
            
            if (properties.getMilvus().isFallbackToMemory()) {
                log.warn("使用内存存储作为向量存储的临时替代方案");
                log.warn("注意：内存存储不会持久化数据，应用重启后数据将丢失");
                return createInMemoryEmbeddingStore();
            } else {
                throw new RuntimeException("无法连接到Milvus向量数据库，且未启用内存存储备用方案", e);
            }
        }
    }
    
    /**
     * 创建内存嵌入存储作为备用
     */
    @Bean(name = "inMemoryEmbeddingStore")
    @ConditionalOnProperty(name = "smartse.vector-store.milvus.enabled", havingValue = "false")
    public EmbeddingStore<TextSegment> createInMemoryEmbeddingStore() {
        log.info("创建内存嵌入存储");
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        log.info("内存嵌入存储创建成功");
        return store;
    }
}