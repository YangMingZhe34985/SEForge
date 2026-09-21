package com.ustb.smartse.modules.knowledgebase.service;

import java.util.Map;

/**
 * 检索增强生成(RAG)服务接口
 */
public interface RAGService {
    
    /**
     * 执行检索增强生成，基于用户问题生成回答
     * @param question 用户问题
     * @return 生成的回答
     */
    String generateAnswer(String question);
    
    /**
     * 执行混合检索增强生成，使用BM25和向量检索结合
     * @param question 用户问题
     * @return 生成的回答和元数据
     */
    Map<String, Object> generateAnswerWithMetadata(String question);
    
    /**
     * 检索并返回相关代码片段
     * @param question 用户问题
     * @param maxResults 最大返回结果数
     * @return 相关代码片段及元数据
     */
    Map<String, Object> retrieveRelevantCodeSnippets(String question, int maxResults);
} 