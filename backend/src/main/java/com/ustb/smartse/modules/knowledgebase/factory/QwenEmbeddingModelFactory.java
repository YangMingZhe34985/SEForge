package com.ustb.smartse.modules.knowledgebase.factory;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;

/**
 * 通义千问嵌入模型工厂类
 * 简化版本，不使用反射设置baseUrl
 */
public class QwenEmbeddingModelFactory {

    /**
     * 创建通义千问嵌入模型实例
     * 
     * @param apiKey DashScope API密钥
     * @param modelName 模型名称，默认为"text-embedding-v2"
     * @param type 模型类型，默认为"general"
     * @return 配置好的QwenEmbeddingModel实例
     */
    public static QwenEmbeddingModel createModel(String apiKey, String modelName, String type) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API密钥不能为空");
        }
        
        // 首先测试DashScope SDK能否正常工作
        testDashscopeSdk(apiKey, modelName);
        
        // 创建QwenEmbeddingModel实例 - 使用默认配置
        System.out.println("创建QwenEmbeddingModel实例...");
        return new QwenEmbeddingModel(apiKey, modelName, type);
    }
    
    /**
     * 测试DashScope SDK是否能正常工作
     * 
     * @param apiKey API密钥
     * @param modelName 模型名称
     */
    private static void testDashscopeSdk(String apiKey, String modelName) {
        try {
            System.out.println("测试DashScope SDK连接...");
            
            // 创建TextEmbedding实例
            TextEmbedding textEmbedding = new TextEmbedding();
            
            // 设置用于测试的简短文本
            String testText = "测试文本";
            
            // 构建参数对象
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .text(testText)
                    .build();
            
            // 打印部分参数信息（不包含apiKey）
            System.out.println("使用模型: " + modelName);
            System.out.println("测试文本: " + testText);
            
            // 执行测试调用
            System.out.println("正在调用DashScope API...");
            textEmbedding.call(param);
            
            System.out.println("DashScope SDK连接测试成功！");
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            Throwable cause = e.getCause();
            String causeMessage = (cause != null) ? cause.getMessage() : "未知";
            
            System.err.println("DashScope SDK测试失败:");
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误消息: " + errorMessage);
            System.err.println("错误原因: " + causeMessage);
            
            throw new RuntimeException("DashScope SDK测试失败: " + errorMessage, e);
        }
    }
} 