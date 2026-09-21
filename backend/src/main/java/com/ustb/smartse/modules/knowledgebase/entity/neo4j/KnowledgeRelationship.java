package com.ustb.smartse.modules.knowledgebase.entity.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

/**
 * Neo4j知识图谱关系实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"targetNode"})
@RelationshipProperties
public class KnowledgeRelationship {
    
    @RelationshipId
    @GeneratedValue
    private Long id;
    
    /**
     * 关系类型（如继承、包含、应用等）
     */
    private String relationType;
    
    /**
     * 关系权重（表示关联强度）
     */
    private Double weight;
    
    /**
     * 目标节点
     */
    @TargetNode
    private KnowledgeNode targetNode;
} 