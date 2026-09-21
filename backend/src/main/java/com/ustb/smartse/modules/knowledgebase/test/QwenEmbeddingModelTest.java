package com.ustb.smartse.modules.knowledgebase.test;

import com.ustb.smartse.modules.knowledgebase.factory.QwenEmbeddingModelFactory;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 通义千问嵌入模型测试类（使用新的工厂方法）
 * 仅在qwen-test配置文件激活时运行
 */
@Component
@Profile("qwen-test")
public class QwenEmbeddingModelTest implements CommandLineRunner {

    @Value("${langchain4j.dashscope.api-key}")
    private String apiKey;

    @Override
    public void run(String... args) {
        System.out.println("========== 开始测试通义千问嵌入模型 (使用工厂方法) ==========");
        
        try {
            // 1. 创建模型实例
            System.out.println("创建模型实例...");
            QwenEmbeddingModel model = QwenEmbeddingModelFactory.createModel(
                    apiKey, 
                    "text-embedding-v2", 
                    "general"
            );
            
            // 2. 执行文本嵌入
            System.out.println("执行文本嵌入...");
            String testText = "软件工程是应用计算机科学、数学、逻辑学及管理学等原理，以工程化方法构建高质量软件的系统方法。";
            Embedding embedding = model.embed(testText).content();
            
            // 3. 打印向量结果
            float[] vector = embedding.vector();
            System.out.println("成功生成向量，维度: " + vector.length);
            System.out.println("向量前10个元素:");
            int numToPrint = Math.min(10, vector.length);
            for (int i = 0; i < numToPrint; i++) {
                System.out.printf("[%d]: %.6f\n", i, vector[i]);
            }
            
            System.out.println("通义千问嵌入模型测试成功!");
            
        } catch (Exception e) {
            System.err.println("通义千问嵌入模型测试失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("========== 通义千问嵌入模型测试结束 ==========");
    }
} 