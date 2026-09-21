package com.ustb.smartse.modules.knowledgebase.test;

import com.ustb.smartse.modules.knowledgebase.config.CustomQwenEmbeddingModel;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG功能测试类
 */
public class RAGTest {
    public static void main(String[] args) {
        System.out.println("开始测试RAG功能...");
        
        try {
            // 创建自定义通义千问向量模型
            String apiKey = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
            CustomQwenEmbeddingModel embeddingModel = new CustomQwenEmbeddingModel(apiKey);
            
            // 连接到Milvus向量数据库
            EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                    .host("localhost")
                    .port(19530)
                    .collectionName("smartse_embeddings")
                    .build();
            
            // 测试查询
            String question = "什么是软件工程？请详细解释其基本概念和重要性。";
            System.out.println("\n查询问题: " + question);
            
            // 使用自定义通义千问模型生成问题的向量嵌入
            System.out.println("正在生成问题的向量嵌入...");
            Embedding questionEmbedding = embeddingModel.embed(question).content();
            
            // 在向量数据库中搜索相似内容
            System.out.println("正在搜索相关内容...");
            List<EmbeddingMatch<TextSegment>> relevantMatches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(questionEmbedding)
                            .maxResults(5)  // 返回前5个最相关的结果
                            .minScore(0.0)  // 最小相似度阈值
                            .build()
            ).matches();
            
            // 打印搜索结果
            System.out.println("\n找到的相关内容：");
            for (int i = 0; i < relevantMatches.size(); i++) {
                EmbeddingMatch<TextSegment> match = relevantMatches.get(i);
                System.out.println("\n--- 匹配 #" + (i + 1) + " (相似度: " + match.score() + ") ---");
                System.out.println("内容: " + match.embedded().text());
                
                Metadata metadata = match.embedded().metadata();
                if (metadata != null) {
                    Map<String, String> metadataMap = new HashMap<>();
                    Map<String, Object> metadataValues = metadata.toMap();
                    for (Map.Entry<String, Object> entry : metadataValues.entrySet()) {
                        metadataMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
                    }
                    System.out.println("元数据: " + metadataMap);
                }
            }
            
            System.out.println("\nRAG功能测试完成！");
            
        } catch (Exception e) {
            System.err.println("RAG功能测试失败: " + e.getMessage());
            e.printStackTrace();
            
            // 追加详细的错误诊断信息
            System.err.println("\n=== 错误诊断信息 ===");
            if (e.getMessage() != null) {
                if (e.getMessage().contains("Connection refused")) {
                    System.err.println("无法连接到Milvus服务器，请确保Milvus服务正在运行。");
                } else if (e.getMessage().contains("Collection not found")) {
                    System.err.println("未找到集合'smartse_embeddings'，请确保已正确创建集合。");
                } else if (e.getMessage().contains("API key")) {
                    System.err.println("API密钥无效或未正确配置。");
                }
            }
        }
    }
} 