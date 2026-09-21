package com.ustb.smartse.modules.knowledgebase.controller;

import com.ustb.smartse.common.api.ApiResult;
import com.ustb.smartse.modules.crawler.service.CrawlerService;
import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;
import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeNode;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeGraphService;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeService;
import com.ustb.smartse.modules.knowledgebase.service.RAGService;
import com.ustb.smartse.modules.knowledgebase.service.ContentProcessingService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 知识库控制器
 */
@Slf4j
@RestController
@RequestMapping("/knowledgebase")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeService knowledgeService;
    
    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    
    @Autowired
    private RAGService ragService;
    
    @Autowired
    private CrawlerService crawlerService;
    
    @Autowired
    private ContentProcessingService contentProcessingService;
    
    /**
     * 添加知识条目
     */
    @PostMapping("/entry")
    public ApiResult<Boolean> addKnowledgeEntry(@RequestBody KnowledgeEntry entry) {
        try {
            boolean result = knowledgeService.addKnowledgeEntry(entry);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("添加知识条目失败", e);
            return ApiResult.error("添加知识条目失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量添加知识条目
     */
    @PostMapping("/entries/batch")
    public ApiResult<Integer> batchAddKnowledgeEntries(@RequestBody List<KnowledgeEntry> entries) {
        try {
            int count = knowledgeService.batchAddKnowledgeEntries(entries);
            return ApiResult.success(count);
        } catch (Exception e) {
            log.error("批量添加知识条目失败", e);
            return ApiResult.error("批量添加知识条目失败: " + e.getMessage());
        }
    }
    
    /**
     * 语义搜索
     */
    @GetMapping("/semantic-search")
    public ApiResult<?> semanticSearch(@RequestParam String query, @RequestParam(defaultValue = "5") int maxResults) {
        try {
            var results = knowledgeService.semanticSearch(query, maxResults);
            return ApiResult.success(results);
        } catch (Exception e) {
            log.error("语义搜索失败", e);
            return ApiResult.error("语义搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 关键词搜索
     */
    @GetMapping("/keyword-search")
    public ApiResult<List<KnowledgeEntry>> keywordSearch(@RequestParam String keyword, @RequestParam(defaultValue = "5") int maxResults) {
        try {
            List<KnowledgeEntry> results = knowledgeService.keywordSearch(keyword, maxResults);
            return ApiResult.success(results);
        } catch (Exception e) {
            log.error("关键词搜索失败", e);
            return ApiResult.error("关键词搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 混合搜索
     */
    @GetMapping("/hybrid-search")
    public ApiResult<?> hybridSearch(@RequestParam String query, @RequestParam(defaultValue = "5") int maxResults) {
        try {
            var results = knowledgeService.hybridSearch(query, maxResults);
            return ApiResult.success(results);
        } catch (Exception e) {
            log.error("混合搜索失败", e);
            return ApiResult.error("混合搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 添加知识图谱节点
     */
    @PostMapping("/graph/node")
    public ApiResult<KnowledgeNode> addKnowledgeNode(@RequestBody KnowledgeNode node) {
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
    @PostMapping("/graph/relationship")
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
     * 查找知识点之间的路径
     */
    @GetMapping("/graph/path")
    public ApiResult<List<KnowledgeNode>> findPath(
            @RequestParam String startNodeName,
            @RequestParam String endNodeName) {
        try {
            List<KnowledgeNode> path = knowledgeGraphService.findPath(startNodeName, endNodeName);
            return ApiResult.success(path);
        } catch (Exception e) {
            log.error("查找知识点路径失败", e);
            return ApiResult.error("查找知识点路径失败: " + e.getMessage());
        }
    }
    
    /**
     * 基于知识图谱进行推理
     */
    @GetMapping("/graph/reasoning")
    public ApiResult<Map<String, Object>> performReasoning(
            @RequestParam String conceptName,
            @RequestParam String ruleName) {
        try {
            Map<String, Object> result = knowledgeGraphService.performReasoning(conceptName, ruleName);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("执行推理失败", e);
            return ApiResult.error("执行推理失败: " + e.getMessage());
        }
    }
    
    /**
     * RAG问答
     */
    @PostMapping("/ask")
    public ApiResult<String> ask(@RequestParam String question) {
        try {
            String answer = ragService.generateAnswer(question);
            return ApiResult.success(answer);
        } catch (Exception e) {
            log.error("问答失败", e);
            return ApiResult.error("问答失败: " + e.getMessage());
        }
    }
    
    /**
     * RAG问答（带元数据）
     */
    @PostMapping("/ask-with-metadata")
    public ApiResult<Map<String, Object>> askWithMetadata(@RequestParam String question) {
        try {
            Map<String, Object> result = ragService.generateAnswerWithMetadata(question);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("问答失败", e);
            return ApiResult.error("问答失败: " + e.getMessage());
        }
    }
    
    /**
     * 检索相关代码片段
     */
    @GetMapping("/code-snippets")
    public ApiResult<Map<String, Object>> retrieveCodeSnippets(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int maxResults) {
        try {
            Map<String, Object> snippets = ragService.retrieveRelevantCodeSnippets(question, maxResults);
            return ApiResult.success(snippets);
        } catch (Exception e) {
            log.error("检索代码片段失败", e);
            return ApiResult.error("检索代码片段失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试知识库功能是否正常工作
     */
    @GetMapping("/test")
    public ApiResult<String> testKnowledgeBase() {
        try {
            // 创建一个测试知识条目
            KnowledgeEntry entry = new KnowledgeEntry();
            entry.setTitle("测试知识条目");
            entry.setContent("这是一个测试知识条目，用于验证知识库功能是否正常工作。");
            entry.setCategory("测试");
            entry.setSourceType(1);
            
            // 保存并转换为向量
            boolean result = knowledgeService.addKnowledgeEntry(entry);
            
            if (result) {
                return ApiResult.success("知识库功能正常工作，测试条目已添加并转换为向量。");
            } else {
                return ApiResult.error("知识条目添加失败");
            }
        } catch (Exception e) {
            log.error("测试知识库功能失败", e);
            return ApiResult.error("测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试语义搜索功能
     */
    @GetMapping("/search-test")
    public ApiResult<List<Map<String, Object>>> testSemanticSearch(@RequestParam String query, 
                                           @RequestParam(defaultValue = "3") int maxResults) {
        try {
            log.info("测试语义搜索: query={}, maxResults={}", query, maxResults);
            
            // 执行语义搜索
            List<TextSegment> segments = knowledgeService.semanticSearch(query, maxResults);
            
            // 转换结果为前端友好的格式
            List<Map<String, Object>> results = segments.stream()
                    .map(segment -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("text", segment.text());
                        
                        // 提取元数据
                        Metadata metadata = segment.metadata();
                        Map<String, Object> metadataMap = metadata.toMap();
                        result.put("metadata", metadataMap);
                        
                        return result;
                    })
                    .collect(Collectors.toList());
            
            log.info("语义搜索测试完成，返回 {} 个结果", results.size());
            return ApiResult.success(results);
        } catch (Exception e) {
            log.error("测试语义搜索失败", e);
            return ApiResult.error("语义搜索测试失败: " + e.getMessage());
        }
    }

    /**
     * 上传PDF并导入知识库
     */
    @PostMapping("/upload-pdf")
    public ApiResult<String> uploadPdf(@RequestParam("file") MultipartFile file,
                                      @RequestParam("category") String category) {
        try {
            // 基本验证
            if (file == null || file.isEmpty()) {
                log.warn("PDF上传失败: 文件为空");
                return ApiResult.error("文件为空，请选择有效的PDF文件");
            }
            
            if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                log.warn("PDF上传失败: 不是PDF文件，文件名: {}", file.getOriginalFilename());
                return ApiResult.error("只支持PDF文件上传");
            }
            
            log.info("接收到PDF上传请求: fileName={}, size={}, category={}", 
                    file.getOriginalFilename(), file.getSize(), category);
            
            // 创建临时文件
            File tempFile = File.createTempFile("upload_", "_pdf");
            file.transferTo(tempFile);
            
            log.info("PDF已保存到临时文件: {}", tempFile.getAbsolutePath());
            
            try {
                // 使用内容处理服务处理PDF
                ContentProcessingService.ProcessOptions options = new ContentProcessingService.ProcessOptions();
                options.setOverwriteExisting(false); // 默认不覆盖
                
                ContentProcessingService.ResourceProcessResult result = contentProcessingService.processResource(
                    tempFile, ContentProcessingService.RESOURCE_TYPE_PDF, category, options);
                
                // 处理结果
                if (result.isSuccess()) {
                    log.info("PDF导入全部流程完成: fileName={}, totalEntries={}, addedEntries={}", 
                            file.getOriginalFilename(), result.getTotalEntries(), result.getAddedEntries());
                    return ApiResult.success("PDF上传并导入成功");
                } else {
                    log.warn("PDF导入失败: {}, 错误: {}", tempFile.getAbsolutePath(), result.getErrorMessage());
                    return ApiResult.error("PDF导入失败: " + result.getErrorMessage());
                }
            } finally {
                // 删除临时文件
                if (!tempFile.delete()) {
                    log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            log.error("PDF上传处理失败", e);
            return ApiResult.error("PDF上传处理失败: " + e.getMessage());
        }
    }
} 