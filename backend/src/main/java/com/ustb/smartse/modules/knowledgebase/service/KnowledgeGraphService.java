package com.ustb.smartse.modules.knowledgebase.service;

import com.ustb.smartse.modules.knowledgebase.dto.BatchImportDTO;
import com.ustb.smartse.modules.knowledgebase.dto.ConceptNodeDTO;
import com.ustb.smartse.modules.knowledgebase.dto.RelationDTO;
import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeNode;
import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeRelation;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱服务接口
 */
public interface KnowledgeGraphService {
    
    /**
     * 保存知识节点
     * 
     * @param node 知识节点
     * @return 保存后的节点
     */
    KnowledgeNode saveNode(KnowledgeNode node);
    
    /**
     * 保存知识关系
     * 
     * @param relation 知识关系
     * @return 保存后的关系
     */
    KnowledgeRelation saveRelation(KnowledgeRelation relation);
    
    /**
     * 根据名称查找节点
     * 
     * @param name 节点名称
     * @return 节点对象
     */
    KnowledgeNode findNodeByName(String name);
    
    /**
     * 根据名称查找节点的所有关系
     * 
     * @param nodeName 节点名称
     * @return 关系列表
     */
    List<KnowledgeRelation> findRelationsByNodeName(String nodeName);
    
    /**
     * 执行知识推理
     * 
     * @param startConcept 起始概念
     * @param query 查询语句
     * @return 推理结果
     */
    Map<String, Object> performReasoning(String startConcept, String query);
    
    /**
     * 查询概念及其关系
     * 
     * @param concept 概念名称
     * @param includeRelations 是否包含关系
     * @param depth 关系深度
     * @return 概念及其关系
     */
    Map<String, Object> queryConcept(String concept, boolean includeRelations, int depth);
    
    /**
     * 执行知识推理
     * 
     * @param startConcept 起始概念
     * @param reasoningDepth 推理深度
     * @param includeExamples 是否包含示例
     * @return 推理结果
     */
    Map<String, Object> performReasoning(String startConcept, int reasoningDepth, boolean includeExamples);
    
    /**
     * 添加或更新概念节点
     * 
     * @param conceptNode 概念节点信息
     * @return 添加/更新后的节点
     */
    ConceptNodeDTO addOrUpdateConcept(ConceptNodeDTO conceptNode);
    
    /**
     * 添加或更新关系
     * 
     * @param relation 关系信息
     * @return 添加/更新后的关系
     */
    RelationDTO addOrUpdateRelation(RelationDTO relation);
    
    /**
     * 删除概念节点
     * 
     * @param conceptName 概念名称
     * @return 是否删除成功
     */
    boolean deleteConcept(String conceptName);
    
    /**
     * 删除关系
     * 
     * @param sourceNode 源节点
     * @param targetNode 目标节点
     * @param relationType 关系类型
     * @return 是否删除成功
     */
    boolean deleteRelation(String sourceNode, String targetNode, String relationType);
    
    /**
     * 添加知识节点
     * 
     * @param node 知识节点
     * @return 添加成功的节点
     */
    KnowledgeNode addKnowledgeNode(KnowledgeNode node);
    
    /**
     * 添加知识点之间的关系
     * 
     * @param sourceNodeId 源节点ID
     * @param targetNodeId 目标节点ID
     * @param relationType 关系类型
     * @param weight 关系权重
     * @return 是否添加成功
     */
    boolean addRelationship(String sourceNodeId, String targetNodeId, String relationType, Double weight);
    
    /**
     * 查找与指定节点相关的所有节点
     * 
     * @param nodeId 节点ID
     * @return 相关节点列表
     */
    List<KnowledgeNode> findRelatedNodes(String nodeId);
    
    /**
     * 查找两个知识点之间的路径
     * 
     * @param startNodeName 起始知识点名称
     * @param endNodeName 终止知识点名称
     * @return 路径上的节点列表
     */
    List<KnowledgeNode> findPath(String startNodeName, String endNodeName);
    
    /**
     * 获取指定类型的所有知识点
     * 
     * @param type 知识点类型
     * @return 知识点列表
     */
    List<KnowledgeNode> getNodesByType(String type);
    
    /**
     * 批量导入知识图谱数据
     * 
     * @param batchData 批量数据
     * @return 导入结果
     */
    Map<String, Object> batchImport(BatchImportDTO batchData);
    
    /**
     * 获取所有知识节点
     * 
     * @return 所有知识节点列表
     */
    List<KnowledgeNode> findAllNodes();
    
    /**
     * 获取所有知识关系
     * 
     * @return 所有知识关系列表
     */
    List<KnowledgeRelation> findAllRelations();

    /**
     * 清空所有知识图谱节点与关系
     */
    void clearAll();
} 