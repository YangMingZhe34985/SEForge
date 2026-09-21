package com.ustb.smartse.modules.knowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 概念节点数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConceptNodeDTO {
    
    /**
     * 节点名称
     */
    private String name;
    
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
     * 节点属性
     */
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
    
    /**
     * 示例
     */
    private String example;
    
    /**
     * 创建时间
     */
    private Long createTime;
    
    /**
     * 更新时间
     */
    private Long updateTime;
} 