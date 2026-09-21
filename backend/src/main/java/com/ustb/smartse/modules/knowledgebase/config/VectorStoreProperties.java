package com.ustb.smartse.modules.knowledgebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "smartse.vector-store")
public class VectorStoreProperties {
    
    /**
     * DashScope配置
     */
    private DashScope dashscope = new DashScope();
    
    /**
     * Milvus配置
     */
    private Milvus milvus = new Milvus();
    
    @Data
    public static class DashScope {
        /**
         * DashScope API密钥
         */
        private String apiKey;
        
        /**
         * 模型名称
         */
        private String model = "text-embedding-v2";
    }
    
    @Data
    public static class Milvus {
        /**
         * Milvus服务器地址
         */
        private String host = "localhost";
        
        /**
         * Milvus服务器端口
         */
        private Integer port = 19530;
        
        /**
         * 数据库名称
         */
        private String database = "default";
        
        /**
         * 集合前缀
         */
        private String collectionPrefix = "smartse_";
        
        /**
         * 一致性级别
         */
        private String consistencyLevel = "EVENTUALLY_CONSISTENT";
        
        /**
         * 向量维度
         */
        private Integer embeddingDimension = 1536;
        
        /**
         * 用户名
         */
        private String username;
        
        /**
         * 密码
         */
        private String password;
        
        /**
         * 是否启用Milvus向量数据库
         */
        private boolean enabled = true;
        
        /**
         * 是否尝试自动加载集合（可能导致gRPC错误）
         */
        private boolean autoLoadCollection = false;
        
        /**
         * 是否在连接失败时使用内存存储作为备用方案
         */
        private boolean fallbackToMemory = true;
    }
} 