package com.ustb.smartse.modules.knowledgebase.service;

import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * 知识库服务接口
 */
public interface KnowledgeService {
    
    /**
     * 添加知识条目到知识库
     * @param entry 知识条目
     * @return 是否添加成功
     */
    boolean addKnowledgeEntry(KnowledgeEntry entry);
    
    /**
     * 批量添加知识条目
     * @param entries 知识条目列表
     * @return 成功添加的数量
     */
    int batchAddKnowledgeEntries(List<KnowledgeEntry> entries);
    
    /**
     * 通过语义检索查询相关知识
     * @param query 查询文本
     * @param maxResults 最大返回结果数
     * @return 相关文本片段
     */
    List<TextSegment> semanticSearch(String query, int maxResults);
    
    /**
     * 通过BM25检索查询相关知识
     * @param keyword 关键词
     * @param maxResults 最大返回结果数
     * @return 知识条目列表
     */
    List<KnowledgeEntry> keywordSearch(String keyword, int maxResults);
    
    /**
     * 混合检索（BM25 + 语义检索）
     * @param query 查询文本
     * @param maxResults 最大返回结果数
     * @return 相关文本片段
     */
    List<TextSegment> hybridSearch(String query, int maxResults);
    
    /**
     * 根据标题查找知识条目
     * @param title 标题
     * @return 知识条目，如果不存在则返回null
     */
    KnowledgeEntry findByTitle(String title);
    
    /**
     * 更新知识条目
     * @param entry 知识条目
     * @return 是否更新成功
     */
    boolean updateKnowledgeEntry(KnowledgeEntry entry);
} 