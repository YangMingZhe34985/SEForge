package com.ustb.smartse.modules.knowledgebase.entity.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * 知识图谱关系实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RelationshipProperties
public class KnowledgeRelation {
    
    @Id
    @GeneratedValue
    private Long id;
    
    /**
     * 源节点ID
     */
    private Long sourceNodeId;
    
    /**
     * 目标节点ID
     */
    private Long targetNodeId;
    
    /**
     * 关系类型
     */
    private String type;
    
    /**
     * 关系置信度
     */
    private Double confidence;
    
    /**
     * 关系示例
     */
    private String example;
    
    /**
     * 关系属性 - 使用JSON字符串存储
     */
    private String propertiesJson;
    
    /**
     * 目标节点
     * Spring Data Neo4j要求@RelationshipProperties类必须有一个@TargetNode字段
     */
    @TargetNode
    private KnowledgeNode targetNode;
    
    /**
     * 创建时间
     */
    @Property("create_time")
    private Long createTime;
    
    /**
     * 更新时间
     */
    @Property("update_time")
    private Long updateTime;
} 