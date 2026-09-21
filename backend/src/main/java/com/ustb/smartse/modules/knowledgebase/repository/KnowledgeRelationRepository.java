package com.ustb.smartse.modules.knowledgebase.repository;

import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeRelation;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识关系仓库接口
 */
@Repository
public interface KnowledgeRelationRepository extends Neo4jRepository<KnowledgeRelation, Long> {
    
    /**
     * 根据源节点ID查找关系
     * 
     * @param sourceNodeId 源节点ID
     * @return 关系列表
     */
    List<KnowledgeRelation> findBySourceNodeId(Long sourceNodeId);
    
    /**
     * 根据目标节点ID查找关系
     * 
     * @param targetNodeId 目标节点ID
     * @return 关系列表
     */
    List<KnowledgeRelation> findByTargetNodeId(Long targetNodeId);
    
    /**
     * 根据源节点ID和目标节点ID查找关系
     * 
     * @param sourceNodeId 源节点ID
     * @param targetNodeId 目标节点ID
     * @return 关系列表
     */
    List<KnowledgeRelation> findBySourceNodeIdAndTargetNodeId(Long sourceNodeId, Long targetNodeId);
    
    /**
     * 根据源节点ID、目标节点ID和关系类型查找关系
     * 
     * @param sourceNodeId 源节点ID
     * @param targetNodeId 目标节点ID
     * @param type 关系类型
     * @return 关系对象
     */
    KnowledgeRelation findBySourceNodeIdAndTargetNodeIdAndType(Long sourceNodeId, Long targetNodeId, String type);
    
    /**
     * 根据源节点ID删除关系
     * 
     * @param sourceNodeId 源节点ID
     */
    void deleteBySourceNodeId(Long sourceNodeId);
    
    /**
     * 根据目标节点ID删除关系
     * 
     * @param targetNodeId 目标节点ID
     */
    void deleteByTargetNodeId(Long targetNodeId);
} 
 