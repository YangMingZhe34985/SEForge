package com.ustb.smartse.api.test;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;

import java.util.Arrays;
import java.util.List;

/**
 * 简单的通义千问Embedding测试
 */
public class SimpleEmbeddingTest {

    public static void main(String[] args) {
        // 设置API KEY
        String apiKey = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
        System.setProperty("DASHSCOPE_API_KEY", apiKey);
        
        System.out.println("开始测试通义千问Embedding API...");
        System.out.println("使用API KEY: " + apiKey);
        
        try {
            // 创建参数对象，使用TEXT_EMBEDDING_V2模型
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                .model(TextEmbedding.Models.TEXT_EMBEDDING_V2)
                .texts(Arrays.asList("智能软件工程是人工智能与软件工程的结合"))
                .build();
                
            // 创建TextEmbedding实例
            TextEmbedding textEmbedding = new TextEmbedding();
            
            System.out.println("发送请求...");
            
            // 调用API
            TextEmbeddingResult result = textEmbedding.call(param);
            
            // 输出结果
            System.out.println("API调用成功！");
            System.out.println("请求ID: " + result.getRequestId());
            
            if (result.getOutput() != null && 
                result.getOutput().getEmbeddings() != null && 
                !result.getOutput().getEmbeddings().isEmpty()) {
                
                // 获取向量值
                List<Double> embedding = result.getOutput().getEmbeddings().get(0).getEmbedding();
                System.out.println("生成向量维度: " + embedding.size());
                System.out.println("向量前3个值: " + embedding.get(0) + ", " + embedding.get(1) + ", " + embedding.get(2));
            }
            
            System.out.println("测试完成！");
            
        } catch (ApiException e) {
            System.err.println("API调用错误: " + e.getMessage());
            e.printStackTrace();
        } catch (NoApiKeyException e) {
            System.err.println("未设置API KEY: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("其他错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 