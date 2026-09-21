package com.ustb.smartse.modules.knowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入知识图谱数据的数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportDTO {
    
    /**
     * 概念节点列表
     */
    @Builder.Default
    private List<ConceptNodeDTO> concepts = new ArrayList<>();
    
    /**
     * 关系列表
     */
    @Builder.Default
    private List<RelationDTO> relations = new ArrayList<>();
} 