package com.ustb.smartse.modules.knowledgebase.service;

import java.util.List;
import java.util.Map;

/**
 * Jena推理服务接口
 */
public interface JenaReasoningService {
    
    /**
     * 执行SPARQL查询
     * 
     * @param queryString 查询字符串
     * @return 查询结果
     */
    List<Map<String, String>> executeQuery(String queryString);
    
    /**
     * 执行推理，查找与概念相关的路径
     * 
     * @param startConcept 起始概念
     * @param reasoningDepth 推理深度
     * @return 推理路径列表
     */
    List<List<Object>> findReasoningPaths(String startConcept, int reasoningDepth);
    
    /**
     * 执行推理，查找与概念相关的应用场景
     * 
     * @param startConcept 起始概念
     * @param query 查询语句
     * @return 推理结果
     */
    Map<String, Object> executeQuery(String startConcept, String query);
    
    /**
     * 推理设计原则的应用场景
     * 
     * @param principleName 原则名称
     * @return 应用场景列表
     */
    List<Map<String, String>> inferPrincipleApplications(String principleName);
    
    /**
     * 推理设计模式的继承关系
     * 
     * @param patternName 模式名称
     * @return 继承关系列表
     */
    List<Map<String, String>> inferPatternInheritance(String patternName);
    
    /**
     * 推理相关的设计模式
     * 
     * @param patternName 模式名称
     * @return 相关模式列表
     */
    List<Map<String, String>> inferRelatedPatterns(String patternName);
    
    /**
     * 将知识条目添加到本体模型
     * @param title 标题
     * @param content 内容
     * @param category 分类
     * @return 是否添加成功
     */
    boolean addKnowledgeToOntology(String title, String content, String category);
} 