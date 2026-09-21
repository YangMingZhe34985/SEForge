package com.ustb.smartse.modules.knowledgebase.service;

import java.util.List;
import java.util.Map;

/**
 * NLP服务接口，用于从文本中提取概念和关系
 */
public interface NlpService {
    
    /**
     * 从文本中提取关键概念
     * @param text 文本内容
     * @param limit 最大提取数量
     * @return 概念列表
     */
    List<String> extractConcepts(String text, int limit);
    
    /**
     * 从文本中提取实体
     * @param text 文本内容
     * @return 实体列表，按类型分组
     */
    Map<String, List<String>> extractEntities(String text);
    
    /**
     * 从文本中提取关系
     * @param text 文本内容
     * @return 关系列表，每个关系包含源实体、关系类型和目标实体
     */
    List<Relationship> extractRelationships(String text);
    
    /**
     * 获取文本的主题或类别
     * @param text 文本内容
     * @return 主题或类别列表
     */
    List<String> extractTopics(String text);
    
    /**
     * 关系类
     */
    class Relationship {
        private String sourceEntity;
        private String relationType;
        private String targetEntity;
        
        public Relationship(String sourceEntity, String relationType, String targetEntity) {
            this.sourceEntity = sourceEntity;
            this.relationType = relationType;
            this.targetEntity = targetEntity;
        }
        
        public String getSourceEntity() {
            return sourceEntity;
        }
        
        public String getRelationType() {
            return relationType;
        }
        
        public String getTargetEntity() {
            return targetEntity;
        }
        
        public void setRelationType(String relationType) {
            this.relationType = relationType;
        }
    }
} 