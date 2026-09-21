package com.ustb.smartse.modules.knowledgebase.config;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通义千问DashScope SDK配置类
 */
@Configuration
public class DashScopeConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(DashScopeConfiguration.class);
    
    private final VectorStoreProperties properties;
    
    public DashScopeConfiguration(VectorStoreProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        // 检查API密钥是否配置
        String apiKey = properties.getDashscope().getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("未配置DashScope API密钥，请在application.yml中配置smartse.vector-store.dashscope.api-key");
            return;
        }
        
        // 设置系统级环境变量
        System.setProperty("DASHSCOPE_API_KEY", apiKey);
        log.info("已设置DashScope API密钥环境变量");
    }
    
    /**
     * 测试DashScope API连接
     */
    private void testDashScopeConnection() throws Exception {
        log.info("正在测试DashScope API连接...");
        
        // 检查API密钥和模型名称
        String apiKey = properties.getDashscope().getApiKey();
        String modelName = properties.getDashscope().getModel();
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("未配置DashScope API密钥");
        }
        
        if (modelName == null || modelName.trim().isEmpty()) {
            log.warn("未配置DashScope模型名称，使用默认值: text-embedding-v2");
            modelName = "text-embedding-v2";
        }
        
        // 创建TextEmbedding实例
        TextEmbedding textEmbedding = new TextEmbedding();
        
        // 构建测试参数
        TextEmbeddingParam param = TextEmbeddingParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .text("测试文本")
                .build();
        
        // 执行API调用
        try {
            textEmbedding.call(param);
            log.info("DashScope API连接测试成功!");
        } catch (Exception e) {
            log.error("DashScope API连接测试失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 创建自定义的嵌入模型
     */
    @Bean
    @Primary
    public EmbeddingModel customQwenEmbeddingModel() {
        log.info("创建自定义通义千问嵌入模型...");
        
        // 检查API密钥是否配置
        String apiKey = properties.getDashscope().getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("未配置DashScope API密钥，无法创建嵌入模型");
            throw new IllegalStateException("未配置DashScope API密钥，请在application.yml中配置smartse.vector-store.dashscope.api-key");
        }
        
        // 测试DashScope API连接
        try {
            testDashScopeConnection();
        } catch (Exception e) {
            log.error("DashScope API连接测试失败: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("DashScope API连接测试失败详情", e);
            }
            log.info("尽管API测试失败，仍继续创建模型实例");
        }
        
        return new CustomQwenEmbeddingModel(apiKey);
    }
} 