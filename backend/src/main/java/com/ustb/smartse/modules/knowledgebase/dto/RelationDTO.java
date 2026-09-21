package com.ustb.smartse.modules.knowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 关系数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationDTO {
    
    /**
     * 源节点名称
     */
    private String sourceNode;
    
    /**
     * 目标节点名称
     */
    private String targetNode;
    
    /**
     * 关系类型
     */
    private String relationType;
    
    /**
     * 关系置信度
     */
    private Double confidence;
    
    /**
     * 关系示例
     */
    private String example;
    
    /**
     * 关系属性
     */
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
    
    /**
     * 创建时间
     */
    private Long createTime;
    
    /**
     * 更新时间
     */
    private Long updateTime;
} 