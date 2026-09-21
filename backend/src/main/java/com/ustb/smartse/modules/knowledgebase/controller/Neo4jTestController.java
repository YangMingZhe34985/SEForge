package com.ustb.smartse.modules.knowledgebase.controller;

import com.ustb.smartse.common.api.ApiResult;
import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeNode;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeGraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Neo4j测试控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/neo4j-test")
public class Neo4jTestController {

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    
    /**
     * 测试Neo4j连接
     */
    @GetMapping("/connection")
    public ApiResult<String> testConnection() {
        try {
            // 创建一个测试节点
            KnowledgeNode testNode = KnowledgeNode.builder()
                    .name("测试节点-" + System.currentTimeMillis())
                    .type("TEST")
                    .definition("这是一个测试节点")
                    .build();
            
            // 保存节点
            KnowledgeNode savedNode = knowledgeGraphService.addKnowledgeNode(testNode);
            
            if (savedNode != null && savedNode.getId() != null) {
                return ApiResult.success("Neo4j连接成功，节点ID: " + savedNode.getId());
            } else {
                return ApiResult.error("Neo4j连接失败，无法保存节点");
            }
        } catch (Exception e) {
            log.error("测试Neo4j连接时发生错误", e);
            return ApiResult.error("Neo4j连接测试失败: " + e.getMessage());
        }
    }
} 