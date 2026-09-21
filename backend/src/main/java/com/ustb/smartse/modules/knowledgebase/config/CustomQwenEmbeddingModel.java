package com.ustb.smartse.modules.knowledgebase.config;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 自定义通义千问嵌入模型实现
 * 直接使用DashScope SDK，避免URL处理问题
 */
@Slf4j
public class CustomQwenEmbeddingModel implements EmbeddingModel {

    private final String apiKey;
    private final String modelName;
    private final TextEmbedding textEmbedding;

    // 缓存，提高性能
    private final ConcurrentHashMap<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    public CustomQwenEmbeddingModel(String apiKey) {
        this(apiKey, "text-embedding-v2");
    }

    public CustomQwenEmbeddingModel(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.textEmbedding = new TextEmbedding();
        
        // 设置系统环境变量，确保API密钥被正确识别
        System.setProperty("DASHSCOPE_API_KEY", apiKey);
    }

    @Override
    public Response<Embedding> embed(String text) {
        try {
            // 先检查缓存
            if (embeddingCache.containsKey(text)) {
                float[] cachedVector = embeddingCache.get(text);
                return Response.from(Embedding.from(cachedVector));
            }

            log.info("生成嵌入向量: {}", text.substring(0, Math.min(50, text.length())) + "...");
            
            // 构建请求参数
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .text(text)
                    .build();

            // 调用API
            TextEmbeddingResult result = textEmbedding.call(param);
            log.info("嵌入向量生成成功, 使用tokens: {}", result.getUsage().getTotalTokens());
            
            // 获取向量
            List<Double> vector = result.getOutput().getEmbeddings().get(0).getEmbedding();
            float[] floatArray = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                floatArray[i] = vector.get(i).floatValue();
            }
            
            // 存入缓存
            embeddingCache.put(text, floatArray);
            
            return Response.from(Embedding.from(floatArray));
        } catch (Exception e) {
            log.error("嵌入向量生成失败", e);
            throw new RuntimeException("生成嵌入向量失败: " + e.getMessage(), e);
        }
    }

    // 实现接口所需的方法，处理文本段列表
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        try {
            // 从TextSegment中提取纯文本内容
            List<String> texts = textSegments.stream()
                    .map(TextSegment::text)
                    .collect(Collectors.toList());
            
            // 调用字符串版本的embedAll方法
            return embedAllTexts(texts);
        } catch (Exception e) {
            log.error("批量生成文本段嵌入向量失败", e);
            throw new RuntimeException("批量生成文本段嵌入向量失败: " + e.getMessage(), e);
        }
    }
    
    // 辅助方法，处理字符串列表
    private Response<List<Embedding>> embedAllTexts(List<String> texts) {
        try {
            List<Embedding> embeddings = new ArrayList<>();
            
            log.info("批量生成嵌入向量, 数量: {}", texts.size());
            
            // 逐个处理文本
            for (String text : texts) {
                Response<Embedding> response = embed(text);
                embeddings.add(response.content());
            }
            
            return Response.from(embeddings);
        } catch (Exception e) {
            log.error("批量生成嵌入向量失败", e);
            throw new RuntimeException("批量生成嵌入向量失败: " + e.getMessage(), e);
        }
    }
} 