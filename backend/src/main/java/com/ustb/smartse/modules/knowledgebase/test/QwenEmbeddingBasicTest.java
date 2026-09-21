package com.ustb.smartse.modules.knowledgebase.test;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Response;

/**
 * 通义千问嵌入模型基础测试类
 * 使用最简单的方式测试QwenEmbeddingModel
 */
public class QwenEmbeddingBasicTest {

    public static void main(String[] args) {
        // API密钥
        String apiKey = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
        
        System.out.println("===== 通义千问嵌入模型基础测试 =====");
        
        try {
            // 1. 创建模型实例（不使用任何自定义配置，只使用必要参数）
            System.out.println("创建QwenEmbeddingModel实例...");
            QwenEmbeddingModel model = new QwenEmbeddingModel(
                    apiKey,                // API密钥
                    "text-embedding-v2",   // 模型名称
                    "general"              // 模型类型
            );
            
            // 2. 准备测试文本
            String text = "软件工程是一门应用计算机科学与技术和工程管理原则的学科";
            System.out.println("测试文本: " + text);
            
            // 3. 执行嵌入生成
            System.out.println("生成嵌入向量...");
            Response<Embedding> response = model.embed(text);
            Embedding embedding = response.content();
            
            // 4. 打印结果
            System.out.println("嵌入向量生成成功!");
            System.out.println("向量维度: " + embedding.vector().length);
            System.out.println("向量前5个值:");
            for (int i = 0; i < Math.min(5, embedding.vector().length); i++) {
                System.out.printf("[%d]: %.6f\n", i, embedding.vector()[i]);
            }
            
            System.out.println("===== 测试完成，结果正常 =====");
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 