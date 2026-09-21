package com.ustb.smartse.modules.knowledgebase.test;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * 通义千问向量模型测试类 - 直接使用DashScope SDK和LangChain4j
 */
public class DashscopeEmbeddingTest {

    /**
     * 主方法，用于测试通义千问向量模型
     */
    public static void main(String[] args) {
        System.out.println("开始测试通义千问向量模型...");
        
        testDirectDashscopeSDK();
        testLangchain4jModel();
    }
    
    /**
     * 使用DashScope SDK直接测试
     */
    private static void testDirectDashscopeSDK() {
        System.out.println("\n===== 测试DashScope SDK直接调用 =====");
        try {
            // 从应用配置中获取的API Key
            String apiKey = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
            
            // 设置测试文本
            String text = "这是一个测试文本，用于验证通义千问的向量嵌入模型是否正常工作。";
            
            // 直接使用Dashscope SDK进行测试
            TextEmbedding textEmbedding = new TextEmbedding();
            
            // 确保设置正确的API参数
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .apiKey(apiKey)
                    .model("text-embedding-v2")  // 使用正确的参数名
                    .text(text)  // 使用单个文本输入
                    .build();
            
            // 打印部分参数信息（不包含apiKey）
            System.out.println("使用模型: " + param.getModel());
            System.out.println("测试文本: " + text);
            
            // 执行测试调用
            System.out.println("正在调用DashScope API...");
            textEmbedding.call(param);
            
            System.out.println("DashScope SDK连接测试成功！");
            
        } catch (Exception e) {
            System.err.println("DashScope SDK测试失败: " + e.getMessage());
            e.printStackTrace();
            
            // 追加详细的错误诊断信息
            System.err.println("\n=== 错误诊断信息 ===");
            System.err.println("错误类型: " + e.getClass().getName());
            
            // 如果是网络错误，提供更多信息
            if (e.getMessage() != null && e.getMessage().contains("network error")) {
                System.err.println("网络错误诊断: 请检查网络连接和防火墙设置");
            }
            
            // 如果是URL格式错误，提供更多信息
            if (e.getMessage() != null && e.getMessage().contains("URL scheme")) {
                System.err.println("URL格式错误: API URL需要包含http/https协议前缀");
            }
            
            Throwable cause = e.getCause();
            if (cause != null) {
                System.err.println("原始错误: " + cause.getMessage());
            }
        }
    }
    
    /**
     * 使用LangChain4j的QwenEmbeddingModel测试
     */
    private static void testLangchain4jModel() {
        System.out.println("\n===== 测试LangChain4j QwenEmbeddingModel =====");
        try {
            // 获取API Key
            String apiKey = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
            
            // 测试文本
            String text = "这是一个测试文本，用于验证通义千问的向量嵌入模型是否正常工作。";
            
            // 创建QwenEmbeddingModel实例
            System.out.println("创建QwenEmbeddingModel实例...");
            QwenEmbeddingModel model = new QwenEmbeddingModel(
                    apiKey,
                    "text-embedding-v2",
                    "general");
            
            // 获取文本的向量嵌入
            System.out.println("生成文本的向量嵌入...");
            Embedding embedding = model.embed(text).content();
            
            // 打印向量维度和部分值
            System.out.println("成功生成向量嵌入!");
            System.out.println("向量维度: " + embedding.vector().length);
            System.out.println("向量前10个值示例:");
            int numToPrint = Math.min(10, embedding.vector().length);
            for (int i = 0; i < numToPrint; i++) {
                System.out.printf("[%d]: %.6f\n", i, embedding.vector()[i]);
            }
            
            System.out.println("LangChain4j QwenEmbeddingModel测试成功!");
            
        } catch (Exception e) {
            System.err.println("LangChain4j QwenEmbeddingModel测试失败: " + e.getMessage());
            e.printStackTrace();
            
            // 追加详细的错误诊断信息
            System.err.println("\n=== 错误诊断信息 ===");
            System.err.println("错误类型: " + e.getClass().getName());
            
            Throwable cause = e.getCause();
            if (cause != null) {
                System.err.println("原始错误: " + cause.getMessage());
                cause.printStackTrace();
            }
        }
    }
} 