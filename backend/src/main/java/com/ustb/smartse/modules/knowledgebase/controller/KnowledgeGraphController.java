package com.ustb.smartse.modules.knowledgebase.controller;

import com.ustb.smartse.common.api.ApiResult;
import com.ustb.smartse.modules.knowledgebase.dto.BatchImportDTO;
import com.ustb.smartse.modules.knowledgebase.dto.ConceptNodeDTO;
import com.ustb.smartse.modules.knowledgebase.dto.RelationDTO;
import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeNode;
import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeRelation;
import com.ustb.smartse.modules.knowledgebase.service.JenaReasoningService;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeGraphService;
import com.ustb.smartse.modules.knowledgebase.util.SoftwareEngineeringKnowledgePresets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * 知识图谱控制器
 * 提供知识图谱的查询、添加、修改等操作
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeGraphController {

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    
    @Autowired
    private JenaReasoningService jenaReasoningService;
    
    /**
     * 查询概念及其关系
     * 
     * @param concept 概念名称
     * @param includeRelations 是否包含关系
     * @param depth 关系深度
     * @return 概念及其关系
     */
    @PostMapping("/query")
    public ApiResult<Map<String, Object>> queryConcept(
            @RequestParam("concept") String concept,
            @RequestParam(value = "includeRelations", defaultValue = "true") boolean includeRelations,
            @RequestParam(value = "depth", defaultValue = "1") int depth) {
        
        try {
            Map<String, Object> result = knowledgeGraphService.queryConcept(concept, includeRelations, depth);
            return ApiResult.success(result, "查询成功");
        } catch (Exception e) {
            log.error("查询概念失败: {}", concept, e);
            return ApiResult.error("查询概念失败: " + e.getMessage());
        }
    }

    /**
     * 知识图谱推理
     * 
     * @param startConcept 起始概念
     * @param reasoningDepth 推理深度
     * @param includeExamples 是否包含示例
     * @return 推理结果
     */
    @PostMapping("/reasoning")
    public ApiResult<Map<String, Object>> reasoning(
            @RequestParam("startConcept") String startConcept,
            @RequestParam(value = "reasoningDepth", defaultValue = "2") int reasoningDepth,
            @RequestParam(value = "includeExamples", defaultValue = "true") boolean includeExamples) {
        
        try {
            Map<String, Object> result = knowledgeGraphService.performReasoning(
                    startConcept, reasoningDepth, includeExamples);
            return ApiResult.success(result, "推理成功");
        } catch (Exception e) {
            log.error("知识图谱推理失败: {}", startConcept, e);
            return ApiResult.error("知识图谱推理失败: " + e.getMessage());
        }
    }

    /**
     * 添加或更新概念节点
     * 
     * @param conceptNode 概念节点信息
     * @return 添加结果
     */
    @PostMapping("/concept")
    public ApiResult<ConceptNodeDTO> addOrUpdateConcept(@RequestBody ConceptNodeDTO conceptNode) {
        try {
            log.info("添加/更新概念节点: {}", conceptNode.getName());
            
            // 参数校验
            if (conceptNode.getName() == null || conceptNode.getName().trim().isEmpty()) {
                return ApiResult.error("概念名称不能为空");
            }
            
            ConceptNodeDTO result = knowledgeGraphService.addOrUpdateConcept(conceptNode);
            return ApiResult.success(result, "概念节点添加/更新成功");
        } catch (Exception e) {
            log.error("添加/更新概念节点失败: {}", conceptNode.getName(), e);
            return ApiResult.error("添加/更新概念节点失败: " + e.getMessage());
        }
    }

    /**
     * 添加或更新关系
     * 
     * @param relation 关系信息
     * @return 添加结果
     */
    @PostMapping("/relation")
    public ApiResult<RelationDTO> addOrUpdateRelation(@RequestBody RelationDTO relation) {
        try {
            log.info("添加/更新关系: {} -> {} -> {}", 
                    relation.getSourceNode(), relation.getRelationType(), relation.getTargetNode());
            
            // 参数校验
            if (relation.getSourceNode() == null || relation.getSourceNode().trim().isEmpty()) {
                return ApiResult.error("源节点不能为空");
            }
            if (relation.getTargetNode() == null || relation.getTargetNode().trim().isEmpty()) {
                return ApiResult.error("目标节点不能为空");
            }
            if (relation.getRelationType() == null || relation.getRelationType().trim().isEmpty()) {
                return ApiResult.error("关系类型不能为空");
            }
            
            RelationDTO result = knowledgeGraphService.addOrUpdateRelation(relation);
            return ApiResult.success(result, "关系添加/更新成功");
        } catch (Exception e) {
            log.error("添加/更新关系失败: {} -> {} -> {}", 
                    relation.getSourceNode(), relation.getRelationType(), relation.getTargetNode(), e);
            return ApiResult.error("添加/更新关系失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除概念节点
     * 
     * @param conceptName 概念名称
     * @return 删除结果
     */
    @DeleteMapping("/concept/{conceptName}")
    public ApiResult<String> deleteConcept(@PathVariable("conceptName") String conceptName) {
        try {
            log.info("删除概念节点: {}", conceptName);
            boolean success = knowledgeGraphService.deleteConcept(conceptName);
            return success 
                ? ApiResult.success("概念节点删除成功") 
                : ApiResult.error("概念节点不存在或删除失败");
        } catch (Exception e) {
            log.error("删除概念节点失败: {}", conceptName, e);
            return ApiResult.error("删除概念节点失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除关系
     * 
     * @param sourceNode 源节点
     * @param targetNode 目标节点
     * @param relationType 关系类型
     * @return 删除结果
     */
    @DeleteMapping("/relation")
    public ApiResult<String> deleteRelation(
            @RequestParam("sourceNode") String sourceNode,
            @RequestParam("targetNode") String targetNode,
            @RequestParam("relationType") String relationType) {
        try {
            log.info("删除关系: {} -> {} -> {}", sourceNode, relationType, targetNode);
            boolean success = knowledgeGraphService.deleteRelation(sourceNode, targetNode, relationType);
            return success 
                ? ApiResult.success("关系删除成功") 
                : ApiResult.error("关系不存在或删除失败");
        } catch (Exception e) {
            log.error("删除关系失败: {} -> {} -> {}", sourceNode, relationType, targetNode, e);
            return ApiResult.error("删除关系失败: " + e.getMessage());
        }
    }

    /**
     * 清空整个知识图谱（所有节点 + 所有关系）
     * 适用于数据重置前的初始化操作
     */
    @DeleteMapping("/clear-all")
    public ApiResult<String> clearAllNodesAndRelations() {
        try {
            log.warn("⚠️ 用户触发清空知识图谱操作！");
            knowledgeGraphService.clearAll();  // 调用 service 中的核心清理方法
            return ApiResult.success("已清空所有知识节点与关系");
        } catch (Exception e) {
            log.error("清空知识图谱失败", e);
            return ApiResult.error("清空失败：" + e.getMessage());
        }
    }



    /**
     * 添加知识图谱节点
     */
    @PostMapping("/nodes")
    public ApiResult<KnowledgeNode> addNode(@RequestBody KnowledgeNode node) {
        try {
            KnowledgeNode savedNode = knowledgeGraphService.addKnowledgeNode(node);
            return ApiResult.success(savedNode);
        } catch (Exception e) {
            log.error("添加知识图谱节点失败", e);
            return ApiResult.error("添加知识图谱节点失败: " + e.getMessage());
        }
    }
    
    /**
     * 添加知识图谱关系
     */
    @PostMapping("/relationships")
    public ApiResult<Boolean> addRelationship(
            @RequestParam String sourceNodeId,
            @RequestParam String targetNodeId,
            @RequestParam String relationType,
            @RequestParam(defaultValue = "1.0") Double weight) {
        try {
            boolean result = knowledgeGraphService.addRelationship(sourceNodeId, targetNodeId, relationType, weight);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("添加知识图谱关系失败", e);
            return ApiResult.error("添加知识图谱关系失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据名称查找节点
     */
    @GetMapping("/nodes")
    public ApiResult<KnowledgeNode> findNodeByName(@RequestParam String name) {
        try {
            KnowledgeNode node = knowledgeGraphService.findNodeByName(name);
            if (node == null) {
                return ApiResult.error("未找到名为 " + name + " 的节点");
            }
            return ApiResult.success(node);
        } catch (Exception e) {
            log.error("查找知识图谱节点失败", e);
            return ApiResult.error("查找知识图谱节点失败: " + e.getMessage());
        }
    }
    
    /**
     * 查找相关节点
     */
    @GetMapping("/nodes/{nodeId}/related")
    public ApiResult<List<KnowledgeNode>> findRelatedNodes(@PathVariable String nodeId) {
        try {
            List<KnowledgeNode> nodes = knowledgeGraphService.findRelatedNodes(nodeId);
            return ApiResult.success(nodes);
        } catch (Exception e) {
            log.error("查找相关节点失败", e);
            return ApiResult.error("查找相关节点失败: " + e.getMessage());
        }
    }
    
    /**
     * 查找节点之间的路径
     */
    @GetMapping("/path")
    public ApiResult<List<KnowledgeNode>> findPath(
            @RequestParam String startNodeName,
            @RequestParam String endNodeName) {
        try {
            List<KnowledgeNode> path = knowledgeGraphService.findPath(startNodeName, endNodeName);
            return ApiResult.success(path);
        } catch (Exception e) {
            log.error("查找路径失败", e);
            return ApiResult.error("查找路径失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取指定类型的节点
     */
    @GetMapping("/nodes/type/{type}")
    public ApiResult<List<KnowledgeNode>> getNodesByType(@PathVariable String type) {
        try {
            List<KnowledgeNode> nodes = knowledgeGraphService.getNodesByType(type);
            return ApiResult.success(nodes);
        } catch (Exception e) {
            log.error("获取节点失败", e);
            return ApiResult.error("获取节点失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行设计原则应用场景推理
     */
    @GetMapping("/reasoning/principle-applications")
    public ApiResult<List<Map<String, String>>> inferPrincipleApplications(@RequestParam String principleName) {
        try {
            List<Map<String, String>> results = jenaReasoningService.inferPrincipleApplications(principleName);
            return ApiResult.success(results);
        } catch (Exception e) {
            log.error("执行推理失败", e);
            return ApiResult.error("执行推理失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行设计模式继承关系推理
     */
    @GetMapping("/reasoning/pattern-inheritance")
    public ApiResult<List<Map<String, String>>> inferPatternInheritance(@RequestParam String patternName) {
        try {
            List<Map<String, String>> results = jenaReasoningService.inferPatternInheritance(patternName);
            return ApiResult.success(results);
        } catch (Exception e) {
            log.error("执行推理失败", e);
            return ApiResult.error("执行推理失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行设计模式关联关系推理
     */
    @GetMapping("/reasoning/related-patterns")
    public ApiResult<List<Map<String, String>>> inferRelatedPatterns(@RequestParam String patternName) {
        try {
            List<Map<String, String>> results = jenaReasoningService.inferRelatedPatterns(patternName);
            return ApiResult.success(results);
        } catch (Exception e) {
            log.error("执行推理失败", e);
            return ApiResult.error("执行推理失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行自定义SPARQL查询
     */
    @PostMapping("/reasoning/query")
    public ApiResult<List<Map<String, String>>> executeQuery(@RequestBody String queryString) {
        try {
            List<Map<String, String>> results = jenaReasoningService.executeQuery(queryString);
            return ApiResult.success(results);
        } catch (Exception e) {
            log.error("执行查询失败", e);
            return ApiResult.error("执行查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量导入知识图谱数据
     * 
     * @param batchData 批量数据
     * @return 导入结果
     */
    @PostMapping("/batch-import")
    public ApiResult<Map<String, Object>> batchImport(@RequestBody BatchImportDTO batchData) {
        try {
            log.info("批量导入知识图谱数据: {} 个概念节点, {} 个关系", 
                    batchData.getConcepts().size(), batchData.getRelations().size());
            
            // 参数校验
            if ((batchData.getConcepts() == null || batchData.getConcepts().isEmpty()) && 
                (batchData.getRelations() == null || batchData.getRelations().isEmpty())) {
                return ApiResult.error("概念节点和关系不能同时为空");
            }
            
            Map<String, Object> result = knowledgeGraphService.batchImport(batchData);
            return ApiResult.success(result, "批量导入成功");
        } catch (Exception e) {
            log.error("批量导入知识图谱数据失败", e);
            return ApiResult.error("批量导入知识图谱数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 一键导入软件工程基础概念
     * 
     * @return 导入结果
     */
    @PostMapping("/presets/import/basic-concepts")
    public ApiResult<Map<String, Object>> importBasicConcepts() {
        try {
            log.info("一键导入软件工程基础概念");
            BatchImportDTO batchData = SoftwareEngineeringKnowledgePresets.getBasicConcepts();
            Map<String, Object> result = knowledgeGraphService.batchImport(batchData);
            return ApiResult.success(result, "软件工程基础概念导入成功");
        } catch (Exception e) {
            log.error("导入软件工程基础概念失败", e);
            return ApiResult.error("导入软件工程基础概念失败: " + e.getMessage());
        }
    }
    
    /**
     * 一键导入软件设计模式
     * 
     * @return 导入结果
     */
    @PostMapping("/presets/import/design-patterns")
    public ApiResult<Map<String, Object>> importDesignPatterns() {
        try {
            log.info("一键导入软件设计模式");
            BatchImportDTO batchData = SoftwareEngineeringKnowledgePresets.getDesignPatterns();
            Map<String, Object> result = knowledgeGraphService.batchImport(batchData);
            return ApiResult.success(result, "软件设计模式导入成功");
        } catch (Exception e) {
            log.error("导入软件设计模式失败", e);
            return ApiResult.error("导入软件设计模式失败: " + e.getMessage());
        }
    }
    
    /**
     * 一键导入软件测试知识
     * 
     * @return 导入结果
     */
    @PostMapping("/presets/import/software-testing")
    public ApiResult<Map<String, Object>> importSoftwareTesting() {
        try {
            log.info("一键导入软件测试知识");
            BatchImportDTO batchData = SoftwareEngineeringKnowledgePresets.getSoftwareTesting();
            Map<String, Object> result = knowledgeGraphService.batchImport(batchData);
            return ApiResult.success(result, "软件测试知识导入成功");
        } catch (Exception e) {
            log.error("导入软件测试知识失败", e);
            return ApiResult.error("导入软件测试知识失败: " + e.getMessage());
        }
    }
    
    /**
     * 一键导入所有软件工程知识图谱数据
     * 
     * @return 导入结果
     */
    @PostMapping("/presets/import/all")
    public ApiResult<Map<String, Object>> importAllPresets() {
        try {
            log.info("一键导入所有软件工程知识图谱数据");
            
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> basicResult = new HashMap<>();
            Map<String, Object> designResult = new HashMap<>();
            Map<String, Object> testingResult = new HashMap<>();
            int totalConcepts = 0;
            int totalRelations = 0;
            
            // 导入基础概念
            BatchImportDTO basicConcepts = SoftwareEngineeringKnowledgePresets.getBasicConcepts();
            basicResult = knowledgeGraphService.batchImport(basicConcepts);
            if (basicResult.containsKey("success") && (boolean) basicResult.get("success")) {
                // 直接从结果中获取导入的概念和关系数量
                if (basicResult.containsKey("importedConcepts") && basicResult.get("importedConcepts") instanceof List) {
                    List<?> concepts = (List<?>) basicResult.get("importedConcepts");
                    totalConcepts += concepts.size();
                }
                if (basicResult.containsKey("importedRelations") && basicResult.get("importedRelations") instanceof List) {
                    List<?> relations = (List<?>) basicResult.get("importedRelations");
                    totalRelations += relations.size();
                }
            }
            
            // 导入设计模式
            BatchImportDTO designPatterns = SoftwareEngineeringKnowledgePresets.getDesignPatterns();
            designResult = knowledgeGraphService.batchImport(designPatterns);
            if (designResult.containsKey("success") && (boolean) designResult.get("success")) {
                // 直接从结果中获取导入的概念和关系数量
                if (designResult.containsKey("importedConcepts") && designResult.get("importedConcepts") instanceof List) {
                    List<?> concepts = (List<?>) designResult.get("importedConcepts");
                    totalConcepts += concepts.size();
                }
                if (designResult.containsKey("importedRelations") && designResult.get("importedRelations") instanceof List) {
                    List<?> relations = (List<?>) designResult.get("importedRelations");
                    totalRelations += relations.size();
                }
            }
            
            // 导入软件测试知识
            BatchImportDTO softwareTesting = SoftwareEngineeringKnowledgePresets.getSoftwareTesting();
            testingResult = knowledgeGraphService.batchImport(softwareTesting);
            if (testingResult.containsKey("success") && (boolean) testingResult.get("success")) {
                // 直接从结果中获取导入的概念和关系数量
                if (testingResult.containsKey("importedConcepts") && testingResult.get("importedConcepts") instanceof List) {
                    List<?> concepts = (List<?>) testingResult.get("importedConcepts");
                    totalConcepts += concepts.size();
                }
                if (testingResult.containsKey("importedRelations") && testingResult.get("importedRelations") instanceof List) {
                    List<?> relations = (List<?>) testingResult.get("importedRelations");
                    totalRelations += relations.size();
                }
            }
            
            result.put("success", true);
            result.put("message", String.format("成功导入 %d 个概念节点和 %d 个关系", totalConcepts, totalRelations));
            result.put("basicConcepts", basicResult);
            result.put("designPatterns", designResult);
            result.put("softwareTesting", testingResult);
            
            return ApiResult.success(result, "软件工程知识图谱数据导入成功");
        } catch (Exception e) {
            log.error("导入所有软件工程知识图谱数据失败", e);
            return ApiResult.error("导入所有软件工程知识图谱数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有节点
     */
    @GetMapping("/nodes/all")
    public ApiResult<List<KnowledgeNode>> getAllNodes() {
        try {
            List<KnowledgeNode> nodes = knowledgeGraphService.findAllNodes();
            return ApiResult.success(nodes);
        } catch (Exception e) {
            log.error("获取所有节点失败", e);
            return ApiResult.error("获取所有节点失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有关系
     */
    @GetMapping("/relations/all")
    public ApiResult<List<KnowledgeRelation>> getAllRelations() {
        try {
            List<KnowledgeRelation> relations = knowledgeGraphService.findAllRelations();
            return ApiResult.success(relations);
        } catch (Exception e) {
            log.error("获取所有关系失败", e);
            return ApiResult.error("获取所有关系失败: " + e.getMessage());
        }
    }

    /**
     * 获取完整的知识图谱数据
     * 返回所有节点和关系的结构化数据
     */
    @GetMapping("/graph")
    public ApiResult<Map<String, Object>> getKnowledgeGraph() {
        try {
            Map<String, Object> graphData = new HashMap<>();
            
            // 获取所有节点
            List<KnowledgeNode> nodes = knowledgeGraphService.findAllNodes();
            
            // 获取所有关系
            List<KnowledgeRelation> relations = knowledgeGraphService.findAllRelations();
            
            // 转换节点为前端可用格式
            List<Map<String, Object>> nodeList = new ArrayList<>();
            for (KnowledgeNode node : nodes) {
                Map<String, Object> nodeMap = new HashMap<>();
                nodeMap.put("id", node.getId());
                nodeMap.put("name", node.getName());
                nodeMap.put("type", node.getType());
                nodeMap.put("definition", node.getDefinition());
                nodeMap.put("categories", node.getCategories());
                nodeList.add(nodeMap);
            }
            
            // 转换关系为前端可用格式
            List<Map<String, Object>> relationList = new ArrayList<>();
            for (KnowledgeRelation relation : relations) {
                Map<String, Object> relationMap = new HashMap<>();
                relationMap.put("id", relation.getId());
                relationMap.put("source", relation.getSourceNodeId());
                relationMap.put("target", relation.getTargetNodeId());
                relationMap.put("type", relation.getType());
                relationMap.put("confidence", relation.getConfidence());
                relationList.add(relationMap);
            }
            
            graphData.put("nodes", nodeList);
            graphData.put("relationships", relationList);
            
            return ApiResult.success(graphData);
        } catch (Exception e) {
            log.error("获取知识图谱数据失败", e);
            return ApiResult.error("获取知识图谱数据失败: " + e.getMessage());
        }
    }
} 