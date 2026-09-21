package com.ustb.smartse.modules.knowledgebase.test;

import com.ustb.smartse.modules.knowledgebase.factory.QwenEmbeddingModelFactory;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;

/**
 * 通义千问向量模型独立测试程序
 * 可以直接运行此类进行测试
 */
public class TestQwenEmbedding {

    public static void main(String[] args) {
        // 获取API密钥，可以从命令行参数传入或直接在此设置
        String apiKey = args.length > 0 ? args[0] : System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
        
        System.out.println("========== 通义千问向量模型独立测试 ==========");
        System.out.println("注意：此测试程序需要有效的通义千问API密钥");
        
        try {
            // 使用工厂方法创建嵌入模型
            System.out.println("\n1. 创建嵌入模型实例...");
            QwenEmbeddingModel model = QwenEmbeddingModelFactory.createModel(
                    apiKey,
                    "text-embedding-v2", 
                    "general");
            
            // 创建测试文本
            String testText = "软件工程是一门研究用工程化方法构建和维护有效、实用和高质量的软件的学科。";
            System.out.println("\n2. 将对以下文本进行向量嵌入:");
            System.out.println("   " + testText);
            
            // 执行向量嵌入
            System.out.println("\n3. 执行向量嵌入...");
            Embedding embedding = model.embed(testText).content();
            
            // 打印嵌入结果
            float[] vector = embedding.vector();
            System.out.println("\n4. 成功生成向量嵌入！");
            System.out.println("   - 向量维度: " + vector.length);
            System.out.println("   - 向量前5个元素示例:");
            for (int i = 0; i < Math.min(5, vector.length); i++) {
                System.out.printf("     [%d]: %.6f\n", i, vector[i]);
            }
            
            System.out.println("\n测试成功完成！通义千问向量模型工作正常。");
            
        } catch (Exception e) {
            System.err.println("\n测试失败! 错误信息:");
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.err.println("\n请检查以下可能的问题:");
            System.err.println("1. API密钥是否有效");
            System.err.println("2. 网络连接是否正常");
            System.err.println("3. 通义千问服务是否可用");
        }
        
        System.out.println("\n========== 通义千问向量模型测试结束 ==========");
    }
} 