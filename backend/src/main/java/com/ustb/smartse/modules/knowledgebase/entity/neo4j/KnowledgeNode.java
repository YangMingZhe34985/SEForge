package com.ustb.smartse.modules.knowledgebase.entity.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱节点实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Node("KnowledgeNode")
public class KnowledgeNode {
    
    @Id
    @GeneratedValue
    private Long id;
    
    /**
     * 节点名称
     */
    private String name;
    
    /**
     * 节点类型
     */
    private String type;
    
    /**
     * 节点定义
     */
    private String definition;
    
    /**
     * 节点分类
     */
    @Builder.Default
    private List<String> categories = new ArrayList<>();
    
    /**
     * 节点属性 - 使用JSON字符串存储
     */
    private String propertiesJson;
    
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
    
    /**
     * 关联的知识条目ID
     */
    private Long knowledgeEntryId;
    
    /**
     * 节点关系
     */
    @Relationship(type = "RELATES_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<KnowledgeRelation> relationships = new ArrayList<>();
} 