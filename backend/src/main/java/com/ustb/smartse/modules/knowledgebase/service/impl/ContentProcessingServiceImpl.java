package com.ustb.smartse.modules.knowledgebase.service.impl;

import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;
import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeNode;
import com.ustb.smartse.modules.knowledgebase.service.ContentProcessingService;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeService;
import com.ustb.smartse.modules.knowledgebase.service.PdfProcessingService;
import com.ustb.smartse.modules.knowledgebase.service.VideoSubtitleService;
import com.ustb.smartse.modules.knowledgebase.service.JenaReasoningService;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeGraphService;
import com.ustb.smartse.modules.knowledgebase.service.NlpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一的内容处理服务实现类
 */
@Slf4j
@Service
public class ContentProcessingServiceImpl implements ContentProcessingService {

    @Autowired
    private PdfProcessingService pdfProcessingService;
    
    @Autowired
    private VideoSubtitleService videoSubtitleService;
    
    @Autowired
    private KnowledgeService knowledgeService;
    
    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    
    @Autowired
    private NlpService nlpService;
    
    @Autowired
    private JenaReasoningService jenaReasoningService;
    
    @Override
    @Transactional
    public ResourceProcessResult processResource(File resourceFile, String resourceType, 
                                               String category, ProcessOptions options) {
        // 根据资源类型调用相应的处理服务
        switch (resourceType) {
            case RESOURCE_TYPE_PDF:
                return processPdf(resourceFile, category, options);
            case RESOURCE_TYPE_VIDEO:
                return processVideo(resourceFile, category, options);
            default:
                log.error("不支持的资源类型: {}", resourceType);
                return ResourceProcessResult.builder()
                    .success(false)
                    .errorMessage("不支持的资源类型: " + resourceType)
                    .build();
        }
    }
    
    /**
     * 处理PDF文件
     */
    private ResourceProcessResult processPdf(File pdfFile, String category, ProcessOptions options) {
        try {
            log.info("开始处理PDF文件: {}, 分类: {}, 覆盖模式: {}", 
                    pdfFile.getName(), category, options.isOverwriteExisting());
            
            // 1. 提取PDF内容
            List<KnowledgeEntry> entries = pdfProcessingService.processPdfToEntries(pdfFile, category);
            
            if (entries.isEmpty()) {
                log.warn("从PDF中未提取到有效内容: {}", pdfFile.getName());
                return ResourceProcessResult.builder()
                    .success(false)
                    .errorMessage("从PDF中未提取到有效内容")
                    .build();
            }
            
            // 2. 根据选项决定是否覆盖已有条目
            int addedCount = 0;
            List<String> failedTitles = new ArrayList<>();
            
            for (KnowledgeEntry entry : entries) {
                boolean success;
                if (options.isOverwriteExisting()) {
                    // 覆盖模式 - 需要在KnowledgeService中实现addOrUpdateKnowledgeEntry方法
                    success = addOrUpdateKnowledgeEntry(entry);
                } else {
                    // 默认模式
                    success = knowledgeService.addKnowledgeEntry(entry);
                }
                
                if (success) {
                    addedCount++;
                    log.info("成功添加知识条目: {}", entry.getTitle());
                    
                    // 构建知识图谱
                    try {
                        buildResourceKnowledgeGraph(entry, "PDF", pdfFile.getName());
                    } catch (Exception e) {
                        log.error("构建知识图谱失败: {}", entry.getTitle(), e);
                    }
                    
                    // 添加到本体模型
                    try {
                        jenaReasoningService.addKnowledgeToOntology(entry.getTitle(), entry.getContent(), entry.getCategory());
                    } catch (Exception e) {
                        log.error("添加到本体模型失败: {}", entry.getTitle(), e);
                    }
                } else {
                    failedTitles.add(entry.getTitle());
                    log.warn("添加知识条目失败: {}", entry.getTitle());
                }
            }
            
            // 3. 返回处理结果
            boolean success = addedCount > 0;
            String message = success ? null : "所有条目添加失败";
            
            return ResourceProcessResult.builder()
                .success(success)
                .errorMessage(message)
                .totalEntries(entries.size())
                .addedEntries(addedCount)
                .failedEntries(failedTitles)
                .build();
                
        } catch (Exception e) {
            log.error("处理PDF文件失败", e);
            return ResourceProcessResult.builder()
                .success(false)
                .errorMessage("处理PDF文件失败: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 处理视频文件
     */
    private ResourceProcessResult processVideo(File videoFile, String category, ProcessOptions options) {
        try {
            log.info("开始处理视频文件: {}, 分类: {}", videoFile.getName(), category);
            
            // 1. 提取视频字幕
            List<String> subtitles = videoSubtitleService.extractSubtitles(videoFile);
            
            if (subtitles.isEmpty()) {
                log.warn("从视频中未提取到字幕: {}", videoFile.getName());
                return ResourceProcessResult.builder()
                    .success(false)
                    .errorMessage("从视频中未提取到字幕")
                    .build();
            }
            
            // 2. 创建知识条目
            KnowledgeEntry entry = KnowledgeEntry.builder()
                .title(videoFile.getName())
                .content(String.join("\n", subtitles))
                .sourceType(5) // 视频字幕
                .sourceId("video_" + java.util.UUID.randomUUID().toString())
                .category(category)
                .createTime(new java.util.Date())
                .updateTime(new java.util.Date())
                .build();
            
            // 3. 添加到知识库
            boolean success;
            if (options.isOverwriteExisting()) {
                success = addOrUpdateKnowledgeEntry(entry);
            } else {
                success = knowledgeService.addKnowledgeEntry(entry);
            }
            
            // 4. 如果添加成功，构建知识图谱
            if (success) {
                try {
                    buildResourceKnowledgeGraph(entry, "VIDEO", videoFile.getName());
                } catch (Exception e) {
                    log.error("构建知识图谱失败: {}", entry.getTitle(), e);
                }
                
                // 添加到本体模型
                try {
                    jenaReasoningService.addKnowledgeToOntology(entry.getTitle(), entry.getContent(), entry.getCategory());
                } catch (Exception e) {
                    log.error("添加到本体模型失败: {}", entry.getTitle(), e);
                }
            }
            
            // 5. 返回处理结果
            if (success) {
                return ResourceProcessResult.builder()
                    .success(true)
                    .totalEntries(1)
                    .addedEntries(1)
                    .build();
            } else {
                List<String> failedTitles = new ArrayList<>();
                failedTitles.add(entry.getTitle());
                
                return ResourceProcessResult.builder()
                    .success(false)
                    .errorMessage("添加视频字幕到知识库失败")
                    .totalEntries(1)
                    .addedEntries(0)
                    .failedEntries(failedTitles)
                    .build();
            }
        } catch (Exception e) {
            log.error("处理视频文件失败", e);
            return ResourceProcessResult.builder()
                .success(false)
                .errorMessage("处理视频文件失败: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 添加或更新知识条目
     * 注意：这是一个临时方法，应该在KnowledgeService中实现
     */
    private boolean addOrUpdateKnowledgeEntry(KnowledgeEntry entry) {
        try {
            // 检查是否存在同名条目
            KnowledgeEntry existingEntry = knowledgeService.findByTitle(entry.getTitle());
            
            if (existingEntry != null) {
                // 更新现有条目
                log.info("更新已存在的知识条目: {}", entry.getTitle());
                entry.setId(existingEntry.getId());
                entry.setUpdateTime(new java.util.Date());
                return knowledgeService.updateKnowledgeEntry(entry);
            } else {
                // 添加新条目
                return knowledgeService.addKnowledgeEntry(entry);
            }
        } catch (Exception e) {
            log.error("添加或更新知识条目失败: {}", entry.getTitle(), e);
            return false;
        }
    }
    
    /**
     * 为资源构建知识图谱
     */
    private void buildResourceKnowledgeGraph(KnowledgeEntry entry, String resourceType, String resourceName) {
        // 1. 创建资源节点
        KnowledgeNode resourceNode = KnowledgeNode.builder()
                .name(resourceName)
                .type(resourceType)
                .definition(resourceType + "资源: " + resourceName)
                .knowledgeEntryId(entry.getId())
                .build();
        
        resourceNode = knowledgeGraphService.addKnowledgeNode(resourceNode);
        
        // 2. 创建知识条目节点
        KnowledgeNode entryNode = KnowledgeNode.builder()
                .name(entry.getTitle())
                .type("ENTRY")
                .definition(entry.getContent().substring(0, Math.min(200, entry.getContent().length())) + "...")
                .knowledgeEntryId(entry.getId())
                .build();
        
        entryNode = knowledgeGraphService.addKnowledgeNode(entryNode);
        
        // 3. 添加资源与条目的关系
        knowledgeGraphService.addRelationship(
                resourceNode.getId().toString(),
                entryNode.getId().toString(),
                "CONTAINS",
                1.0);
        
        // 4. 提取关键概念
        List<String> concepts = nlpService.extractConcepts(entry.getContent(), 10);
        
        // 5. 为每个概念创建节点并与条目建立关系
        for (String concept : concepts) {
            // 检查概念节点是否已存在
            KnowledgeNode existingNode = knowledgeGraphService.findNodeByName(concept);
            
            KnowledgeNode conceptNode;
            if (existingNode == null) {
                // 创建新节点
                conceptNode = KnowledgeNode.builder()
                        .name(concept)
                        .type("CONCEPT")
                        .definition("从" + entry.getTitle() + "中提取的概念")
                        .knowledgeEntryId(entry.getId())
                        .build();
                
                conceptNode = knowledgeGraphService.addKnowledgeNode(conceptNode);
            } else {
                conceptNode = existingNode;
            }
            
            // 添加关系
            knowledgeGraphService.addRelationship(
                    entryNode.getId().toString(),
                    conceptNode.getId().toString(),
                    "CONTAINS",
                    1.0);
        }
        
        // 6. 根据分类添加分类节点
        KnowledgeNode categoryNode = knowledgeGraphService.findNodeByName(entry.getCategory());
        if (categoryNode == null) {
            categoryNode = KnowledgeNode.builder()
                    .name(entry.getCategory())
                    .type("CATEGORY")
                    .definition("知识分类")
                    .knowledgeEntryId(entry.getId())
                    .build();
            
            categoryNode = knowledgeGraphService.addKnowledgeNode(categoryNode);
        }
        
        // 添加分类关系
        knowledgeGraphService.addRelationship(
                entryNode.getId().toString(),
                categoryNode.getId().toString(),
                "BELONGS_TO",
                1.0);
    }
} 