package com.ustb.smartse.modules.knowledgebase.repository;

import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识节点仓库接口
 */
@Repository
public interface KnowledgeNodeRepository extends Neo4jRepository<KnowledgeNode, Long> {
    
    /**
     * 根据名称查找节点
     * 
     * @param name 节点名称
     * @return 节点对象
     */
    KnowledgeNode findByName(String name);
    
    /**
     * 根据类型查找节点
     * 
     * @param type 节点类型
     * @return 节点列表
     */
    List<KnowledgeNode> findByType(String type);
    
    /**
     * 查找与指定节点相关的所有节点
     * 
     * @param nodeId 节点ID
     * @return 相关节点列表
     */
    @Query("MATCH (n:KnowledgeNode)-[r:RELATES_TO]-(related) WHERE n.id = $nodeId RETURN related")
    List<KnowledgeNode> findRelatedNodes(@Param("nodeId") Long nodeId);
    
    /**
     * 查找两个节点之间的最短路径
     * 
     * @param startNodeName 起始节点名称
     * @param endNodeName 终止节点名称
     * @param maxDepth 最大深度
     * @return 路径上的节点列表
     */
    @Query("MATCH path = shortestPath((start:KnowledgeNode {name: $startNodeName})-[*...$maxDepth]-(end:KnowledgeNode {name: $endNodeName})) " +
           "RETURN nodes(path) as nodes")
    List<KnowledgeNode> findShortestPath(
            @Param("startNodeName") String startNodeName, 
            @Param("endNodeName") String endNodeName, 
            @Param("maxDepth") int maxDepth);
    
    /**
     * 根据名称查找节点是否存在
     * 
     * @param name 节点名称
     * @return 是否存在
     */
    boolean existsByName(String name);
} 