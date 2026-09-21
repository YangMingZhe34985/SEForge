package com.ustb.smartse.modules.knowledgebase.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.smartse.modules.knowledgebase.dto.BatchImportDTO;
import com.ustb.smartse.modules.knowledgebase.dto.ConceptNodeDTO;
import com.ustb.smartse.modules.knowledgebase.dto.RelationDTO;
import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeNode;
import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeRelation;
import com.ustb.smartse.modules.knowledgebase.repository.KnowledgeNodeRepository;
import com.ustb.smartse.modules.knowledgebase.repository.KnowledgeRelationRepository;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeGraphService;
import com.ustb.smartse.modules.knowledgebase.service.JenaReasoningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 知识图谱服务实现类
 */
@Slf4j
@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    @Autowired
    private KnowledgeNodeRepository nodeRepository;
    
    @Autowired
    private KnowledgeRelationRepository relationRepository;
    
    @Autowired
    private Neo4jTemplate neo4jTemplate;
    
    @Autowired
    private JenaReasoningService jenaReasoningService;

    @Override
    @Transactional
    public KnowledgeNode addKnowledgeNode(KnowledgeNode node) {
        try {
            return nodeRepository.save(node);
        } catch (Exception e) {
            log.error("添加知识节点失败", e);
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean addRelationship(String sourceNodeId, String targetNodeId, String relationType, Double weight) {
        try {
            Optional<KnowledgeNode> sourceNodeOpt = nodeRepository.findById(Long.parseLong(sourceNodeId));
            Optional<KnowledgeNode> targetNodeOpt = nodeRepository.findById(Long.parseLong(targetNodeId));
            
            if (sourceNodeOpt.isPresent() && targetNodeOpt.isPresent()) {
                KnowledgeNode sourceNode = sourceNodeOpt.get();
                KnowledgeNode targetNode = targetNodeOpt.get();
                
                KnowledgeRelation relationship = KnowledgeRelation.builder()
                        .type(relationType)
                        .confidence(weight)
                        .sourceNodeId(sourceNode.getId())
                        .targetNodeId(targetNode.getId())
                        .targetNode(targetNode)
                        .createTime(System.currentTimeMillis())
                        .updateTime(System.currentTimeMillis())
                        .build();
                
                // 将关系添加到源节点的relationships列表中
                sourceNode.getRelationships().add(relationship);
                
                // 保存源节点，这将级联保存关系
                nodeRepository.save(sourceNode);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("添加关系失败", e);
            return false;
        }
    }

    @Override
    public KnowledgeNode findNodeByName(String name) {
        try {
            return nodeRepository.findByName(name);
        } catch (Exception e) {
            log.error("查找知识节点失败", e);
            return null;
        }
    }

    @Override
    public List<KnowledgeNode> findRelatedNodes(String nodeId) {
        try {
            return nodeRepository.findRelatedNodes(Long.parseLong(nodeId));
        } catch (Exception e) {
            log.error("查找相关节点失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<KnowledgeNode> findPath(String startNodeName, String endNodeName) {
        try {
            // 最大深度设置为5，避免搜索过深
            return nodeRepository.findShortestPath(startNodeName, endNodeName, 5);
        } catch (Exception e) {
            log.error("查找路径失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<KnowledgeNode> getNodesByType(String type) {
        try {
            return nodeRepository.findByType(type);
        } catch (Exception e) {
            log.error("按类型查找节点失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> performReasoning(String conceptName, String ruleName) {
        try {
            return jenaReasoningService.executeQuery(conceptName, ruleName);
        } catch (Exception e) {
            log.error("执行推理失败", e);
            return Map.of("error", "推理过程中发生错误: " + e.getMessage());
        }
    }

    @Override
    public KnowledgeNode saveNode(KnowledgeNode node) {
        return nodeRepository.save(node);
    }

    @Override
    public KnowledgeRelation saveRelation(KnowledgeRelation relation) {
        return relationRepository.save(relation);
    }

    @Override
    public List<KnowledgeRelation> findRelationsByNodeName(String nodeName) {
        KnowledgeNode node = nodeRepository.findByName(nodeName);
        if (node == null) {
            return Collections.emptyList();
        }
        
        return relationRepository.findBySourceNodeId(node.getId());
    }

    @Override
    public Map<String, Object> queryConcept(String concept, boolean includeRelations, int depth) {
        Map<String, Object> result = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 查询概念节点
        KnowledgeNode node = nodeRepository.findByName(concept);
        if (node == null) {
            return result;
        }
        
        // 构建概念信息
        Map<String, Object> conceptInfo = new HashMap<>();
        conceptInfo.put("name", node.getName());
        conceptInfo.put("definition", node.getDefinition());
        conceptInfo.put("categories", node.getCategories());
        
        // 解析JSON属性
        Map<String, Object> properties = new HashMap<>();
        try {
            if (node.getPropertiesJson() != null && !node.getPropertiesJson().isEmpty()) {
                properties = objectMapper.readValue(node.getPropertiesJson(), Map.class);
            }
        } catch (IOException e) {
            log.error("JSON转换为属性失败", e);
        }
        conceptInfo.put("properties", properties);
        
        result.put("concept", conceptInfo);
        
        // 如果需要包含关系
        if (includeRelations) {
            List<Map<String, Object>> relations = new ArrayList<>();
            
            // 获取直接关系
            List<KnowledgeRelation> directRelations = findRelationsByNodeName(concept);
            for (KnowledgeRelation relation : directRelations) {
                Map<String, Object> relationInfo = new HashMap<>();
                relationInfo.put("type", relation.getType());
                relationInfo.put("target", relation.getTargetNode().getName());
                relationInfo.put("confidence", relation.getConfidence());
                
                // 解析JSON属性
                Map<String, Object> relationProperties = new HashMap<>();
                try {
                    if (relation.getPropertiesJson() != null && !relation.getPropertiesJson().isEmpty()) {
                        relationProperties = objectMapper.readValue(relation.getPropertiesJson(), Map.class);
                    }
                } catch (IOException e) {
                    log.error("JSON转换为属性失败", e);
                }
                relationInfo.put("properties", relationProperties);
                
                relations.add(relationInfo);
            }
            
            // 如果深度大于1，递归获取更深层次的关系
            if (depth > 1) {
                for (KnowledgeRelation relation : directRelations) {
                    String targetName = relation.getTargetNode().getName();
                    Map<String, Object> deeperRelations = queryConcept(targetName, true, depth - 1);
                    if (deeperRelations.containsKey("relations")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> deeperRelationsList = 
                                (List<Map<String, Object>>) deeperRelations.get("relations");
                        relations.addAll(deeperRelationsList);
                    }
                }
            }
            
            result.put("relations", relations);
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> performReasoning(String startConcept, int reasoningDepth, boolean includeExamples) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> paths = new ArrayList<>();
        
        // 获取起始概念
        KnowledgeNode startNode = nodeRepository.findByName(startConcept);
        if (startNode == null) {
            result.put("error", "起始概念不存在");
            return result;
        }
        
        // 执行推理
        List<List<Object>> reasoningPaths = jenaReasoningService.findReasoningPaths(
                startConcept, reasoningDepth);
        
        // 处理推理结果
        for (List<Object> path : reasoningPaths) {
            Map<String, Object> pathInfo = new HashMap<>();
            pathInfo.put("path", path);
            
            // 计算置信度
            double confidence = calculatePathConfidence(path);
            pathInfo.put("confidence", confidence);
            
            // 如果需要包含示例
            if (includeExamples) {
                String example = generatePathExample(path);
                pathInfo.put("example", example);
            }
            
            paths.add(pathInfo);
        }
        
        result.put("paths", paths);
        return result;
    }
    
    /**
     * 计算路径的置信度
     */
    private double calculatePathConfidence(List<Object> path) {
        // 简单实现：路径越长，置信度越低
        return Math.max(0.1, 1.0 - (path.size() - 1) * 0.1);
    }
    
    /**
     * 生成路径示例
     */
    private String generatePathExample(List<Object> path) {
        // 简单实现：拼接路径中的概念和关系
        return "在" + path.get(0) + "中，" + 
               String.join(" ", path.stream().map(Object::toString).collect(Collectors.toList()));
    }

    @Override
    @Transactional
    public ConceptNodeDTO addOrUpdateConcept(ConceptNodeDTO conceptNode) {
        // 检查是否已存在该概念
        KnowledgeNode existingNode = nodeRepository.findByName(conceptNode.getName());
        
        // 创建ObjectMapper用于JSON转换
        ObjectMapper objectMapper = new ObjectMapper();
        
        KnowledgeNode node;
        Long currentTime = System.currentTimeMillis();
        
        if (existingNode != null) {
            // 更新现有节点
            node = existingNode;
            log.info("更新节点: {}, 原createTime: {}", node.getName(), node.getCreateTime());
            node.setDefinition(conceptNode.getDefinition());
            node.setCategories(conceptNode.getCategories());
            
            // 将属性Map转换为JSON字符串
            try {
                if (conceptNode.getAttributes() != null) {
                    node.setPropertiesJson(objectMapper.writeValueAsString(conceptNode.getAttributes()));
                }
            } catch (JsonProcessingException e) {
                log.error("属性转换为JSON失败", e);
                node.setPropertiesJson("{}");
            }
            
            // 只更新updateTime，保留原有的createTime
            node.setUpdateTime(currentTime);
            
            // 如果createTime为空，则设置createTime
            if (node.getCreateTime() == null) {
                log.info("节点createTime为空，设置为当前时间: {}", currentTime);
                node.setCreateTime(currentTime);
            }
        } else {
            // 创建新节点
            String propertiesJson = "{}";
            try {
                if (conceptNode.getAttributes() != null) {
                    propertiesJson = objectMapper.writeValueAsString(conceptNode.getAttributes());
                }
            } catch (JsonProcessingException e) {
                log.error("属性转换为JSON失败", e);
            }
            
            node = KnowledgeNode.builder()
                    .name(conceptNode.getName())
                    .definition(conceptNode.getDefinition())
                    .categories(conceptNode.getCategories())
                    .propertiesJson(propertiesJson)
                    .createTime(currentTime)
                    .updateTime(currentTime)
                    .build();
            log.info("创建新节点: {}, createTime: {}", node.getName(), node.getCreateTime());
        }
        
        // 保存节点
        node = nodeRepository.save(node);
        log.info("保存后节点: {}, createTime: {}, updateTime: {}", node.getName(), node.getCreateTime(), node.getUpdateTime());
        
        // 转换为DTO返回
        Map<String, Object> attributes = new HashMap<>();
        try {
            if (node.getPropertiesJson() != null && !node.getPropertiesJson().isEmpty()) {
                attributes = objectMapper.readValue(node.getPropertiesJson(), Map.class);
            }
        } catch (IOException e) {
            log.error("JSON转换为属性失败", e);
        }
        
        // 确保createTime不为空
        Long createTimeValue = node.getCreateTime();
        if (createTimeValue == null) {
            createTimeValue = currentTime;
            log.info("保存后节点createTime仍为空，使用当前时间: {}", createTimeValue);
        }
        
        // 确保返回正确的createTime
        ConceptNodeDTO result = ConceptNodeDTO.builder()
                .name(node.getName())
                .definition(node.getDefinition())
                .categories(node.getCategories())
                .attributes(attributes)
                .example(conceptNode.getExample())
                .createTime(createTimeValue)
                .updateTime(node.getUpdateTime())
                .build();
        
        log.info("返回DTO: {}, createTime: {}, updateTime: {}", result.getName(), result.getCreateTime(), result.getUpdateTime());
        return result;
    }

    @Override
    @Transactional
    public RelationDTO addOrUpdateRelation(RelationDTO relation) {
        // 查找源节点和目标节点
        KnowledgeNode sourceNode = nodeRepository.findByName(relation.getSourceNode());
        KnowledgeNode targetNode = nodeRepository.findByName(relation.getTargetNode());
        
        // 创建ObjectMapper用于JSON转换
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 如果节点不存在，先创建节点
        if (sourceNode == null) {
            sourceNode = KnowledgeNode.builder()
                    .name(relation.getSourceNode())
                    .createTime(System.currentTimeMillis())
                    .updateTime(System.currentTimeMillis())
                    .build();
            sourceNode = nodeRepository.save(sourceNode);
        }
        
        if (targetNode == null) {
            targetNode = KnowledgeNode.builder()
                    .name(relation.getTargetNode())
                    .createTime(System.currentTimeMillis())
                    .updateTime(System.currentTimeMillis())
                    .build();
            targetNode = nodeRepository.save(targetNode);
        }
        
        // 查找是否已存在该关系
        KnowledgeRelation existingRelation = relationRepository.findBySourceNodeIdAndTargetNodeIdAndType(
                sourceNode.getId(), targetNode.getId(), relation.getRelationType());
        
        KnowledgeRelation knowledgeRelation;
        if (existingRelation != null) {
            // 更新现有关系
            knowledgeRelation = existingRelation;
            knowledgeRelation.setConfidence(relation.getConfidence());
            knowledgeRelation.setExample(relation.getExample());
            
            // 将属性Map转换为JSON字符串
            try {
                if (relation.getAttributes() != null) {
                    knowledgeRelation.setPropertiesJson(objectMapper.writeValueAsString(relation.getAttributes()));
                }
            } catch (JsonProcessingException e) {
                log.error("属性转换为JSON失败", e);
                knowledgeRelation.setPropertiesJson("{}");
            }
            
            knowledgeRelation.setUpdateTime(System.currentTimeMillis());
            knowledgeRelation.setTargetNode(targetNode);
            
            // 保存关系
            knowledgeRelation = relationRepository.save(knowledgeRelation);
        } else {
            // 创建新关系
            String propertiesJson = "{}";
            try {
                if (relation.getAttributes() != null) {
                    propertiesJson = objectMapper.writeValueAsString(relation.getAttributes());
                }
            } catch (JsonProcessingException e) {
                log.error("属性转换为JSON失败", e);
            }
            
            // 保存targetNode的ID，用于后续查找
            final Long targetNodeId = targetNode.getId();
            final String relationTypeFinal = relation.getRelationType();
            
            knowledgeRelation = KnowledgeRelation.builder()
                    .type(relationTypeFinal)
                    .confidence(relation.getConfidence())
                    .example(relation.getExample())
                    .propertiesJson(propertiesJson)
                    .sourceNodeId(sourceNode.getId())
                    .targetNodeId(targetNodeId)
                    .targetNode(targetNode)
                    .createTime(System.currentTimeMillis())
                    .updateTime(System.currentTimeMillis())
                    .build();
            
            // 将关系添加到源节点的relationships列表中
            sourceNode.getRelationships().add(knowledgeRelation);
            
            // 保存源节点，这将级联保存关系
            sourceNode = nodeRepository.save(sourceNode);
            
            // 获取保存后的关系
            knowledgeRelation = sourceNode.getRelationships().stream()
                .filter(rel -> rel.getTargetNodeId().equals(targetNodeId) && 
                       rel.getType().equals(relationTypeFinal))
                .findFirst()
                .orElse(knowledgeRelation);
        }
        
        // 转换为DTO返回
        Map<String, Object> attributes = new HashMap<>();
        try {
            if (knowledgeRelation.getPropertiesJson() != null && !knowledgeRelation.getPropertiesJson().isEmpty()) {
                attributes = objectMapper.readValue(knowledgeRelation.getPropertiesJson(), Map.class);
            }
        } catch (IOException e) {
            log.error("JSON转换为属性失败", e);
        }
        
        return RelationDTO.builder()
                .sourceNode(sourceNode.getName())
                .targetNode(targetNode.getName())
                .relationType(knowledgeRelation.getType())
                .confidence(knowledgeRelation.getConfidence())
                .example(knowledgeRelation.getExample())
                .attributes(attributes)
                .createTime(knowledgeRelation.getCreateTime())
                .updateTime(knowledgeRelation.getUpdateTime())
                .build();
    }

    @Override
    @Transactional
    public boolean deleteConcept(String conceptName) {
        KnowledgeNode node = nodeRepository.findByName(conceptName);
        if (node == null) {
            return false;
        }
        
        // 删除与该节点相关的所有关系
        relationRepository.deleteBySourceNodeId(node.getId());
        relationRepository.deleteByTargetNodeId(node.getId());
        
        // 删除节点
        nodeRepository.delete(node);
        return true;
    }

    @Override
    @Transactional
    public boolean deleteRelation(String sourceNode, String targetNode, String relationType) {
        // 查找源节点和目标节点
        KnowledgeNode source = nodeRepository.findByName(sourceNode);
        KnowledgeNode target = nodeRepository.findByName(targetNode);
        
        if (source == null || target == null) {
            return false;
        }
        
        // 查找关系
        KnowledgeRelation relation = relationRepository.findBySourceNodeIdAndTargetNodeIdAndType(
                source.getId(), target.getId(), relationType);
        
        if (relation == null) {
            return false;
        }
        
        // 删除关系
        relationRepository.delete(relation);
        return true;
    }

    @Override
    @Transactional
    public Map<String, Object> batchImport(BatchImportDTO batchData) {
        Map<String, Object> result = new HashMap<>();
        List<ConceptNodeDTO> importedConcepts = new ArrayList<>();
        List<RelationDTO> importedRelations = new ArrayList<>();
        int conceptsCount = 0;
        int relationsCount = 0;
        
        try {
            // 导入概念节点
            if (batchData.getConcepts() != null && !batchData.getConcepts().isEmpty()) {
                for (ConceptNodeDTO concept : batchData.getConcepts()) {
                    try {
                        ConceptNodeDTO importedConcept = addOrUpdateConcept(concept);
                        importedConcepts.add(importedConcept);
                        conceptsCount++;
                    } catch (Exception e) {
                        log.error("导入概念节点失败: {}", concept.getName(), e);
                    }
                }
            }
            
            // 导入关系
            if (batchData.getRelations() != null && !batchData.getRelations().isEmpty()) {
                for (RelationDTO relation : batchData.getRelations()) {
                    try {
                        RelationDTO importedRelation = addOrUpdateRelation(relation);
                        importedRelations.add(importedRelation);
                        relationsCount++;
                    } catch (Exception e) {
                        log.error("导入关系失败: {} -> {} -> {}", 
                                relation.getSourceNode(), relation.getRelationType(), relation.getTargetNode(), e);
                    }
                }
            }
            
            result.put("success", true);
            result.put("message", String.format("成功导入 %d 个概念节点和 %d 个关系", conceptsCount, relationsCount));
            result.put("importedConcepts", importedConcepts);
            result.put("importedRelations", importedRelations);
            
        } catch (Exception e) {
            log.error("批量导入知识图谱数据失败", e);
            result.put("success", false);
            result.put("message", "批量导入知识图谱数据失败: " + e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public List<KnowledgeNode> findAllNodes() {
        try {
            return nodeRepository.findAll();
        } catch (Exception e) {
            log.error("获取所有节点失败", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<KnowledgeRelation> findAllRelations() {
        try {
            return relationRepository.findAll();
        } catch (Exception e) {
            log.error("获取所有关系失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 清空所有知识图谱数据：先删除所有关系，再删除所有节点
     */
    @Override
    @Transactional
    public void clearAll() {
        log.warn("⚠️ 正在清空知识图谱：删除所有关系...");
        relationRepository.deleteAll();

        log.warn("⚠️ 正在清空知识图谱：删除所有节点...");
        nodeRepository.deleteAll();

        log.info("✅ 知识图谱已清空完毕");
    }

} 