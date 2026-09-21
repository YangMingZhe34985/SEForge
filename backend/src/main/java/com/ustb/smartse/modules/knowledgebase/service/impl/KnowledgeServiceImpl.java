package com.ustb.smartse.modules.knowledgebase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;
import com.ustb.smartse.modules.knowledgebase.entity.neo4j.KnowledgeNode;
import com.ustb.smartse.modules.knowledgebase.mapper.KnowledgeEntryMapper;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeService;
import com.ustb.smartse.modules.knowledgebase.service.JenaReasoningService;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeGraphService;
import com.ustb.smartse.modules.knowledgebase.service.NlpService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeEntryMapper, KnowledgeEntry> implements KnowledgeService {

    @Autowired
    private KnowledgeEntryMapper knowledgeEntryMapper;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;
    
    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    
    @Autowired
    private NlpService nlpService;
    
    @Autowired
    private JenaReasoningService jenaReasoningService;

    @Override
    @Transactional
    public boolean addKnowledgeEntry(KnowledgeEntry entry) {
        try {
            log.info("开始添加知识条目: {}", entry.getTitle());
            
            // 检查重复
            LambdaQueryWrapper<KnowledgeEntry> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(KnowledgeEntry::getTitle, entry.getTitle());
            KnowledgeEntry existEntry = knowledgeEntryMapper.selectOne(queryWrapper);
            
            if (existEntry != null) {
                log.warn("知识条目已存在: {}", entry.getTitle());
                return false;
            }
            
            // 保存到数据库
            log.info("保存知识条目到数据库");
            entry.setCreateTime(new Date());
            knowledgeEntryMapper.insert(entry);
            
            // 转换为嵌入向量并保存到向量存储
            try {
                log.info("开始生成知识条目的嵌入向量");
                String textToEmbed = entry.getTitle() + ": " + entry.getContent();
                Embedding embedding = embeddingModel.embed(textToEmbed).content();
                
                // 创建适合新版API的元数据
                log.info("创建元数据并构建文本段");
                Map<String, Object> metadataMap = new HashMap<>();
                metadataMap.put("id", String.valueOf(entry.getId()));
                metadataMap.put("title", entry.getTitle());
                metadataMap.put("category", entry.getCategory());
                Metadata metadata = Metadata.from(metadataMap);
                TextSegment segment = TextSegment.from(textToEmbed, metadata);
                
                // 新版API直接添加TextSegment
                log.info("添加向量到向量存储");
                String vectorId = embeddingStore.add(embedding, segment);
                
                // 更新vectorId
                log.info("更新知识条目的向量ID: {}", vectorId);
                entry.setVectorId(vectorId);
                knowledgeEntryMapper.updateById(entry);
                
                log.info("知识条目添加成功: {}, 向量ID: {}", entry.getTitle(), vectorId);
            } catch (Exception e) {
                log.error("向量处理异常", e);
                // 虽然向量处理失败，但数据已保存到数据库，所以不抛出异常
            }
            
            // 自动构建知识图谱
            try {
                log.info("开始自动构建知识图谱...");
                buildKnowledgeGraph(entry);
                log.info("知识图谱构建完成");
            } catch (Exception e) {
                log.error("知识图谱构建失败", e);
                // 不影响知识条目的添加
            }
            
            // 添加到本体模型
            try {
                log.info("开始添加到本体模型...");
                jenaReasoningService.addKnowledgeToOntology(entry.getTitle(), entry.getContent(), entry.getCategory());
                log.info("添加到本体模型完成");
            } catch (Exception e) {
                log.error("添加到本体模型失败", e);
                // 不影响知识条目的添加
            }
            
            return true;
        } catch (Exception e) {
            log.error("添加知识条目失败", e);
            return false;
        }
    }
    
    /**
     * 构建知识图谱
     */
    private void buildKnowledgeGraph(KnowledgeEntry entry) {
        try {
            log.info("开始构建知识图谱，限制处理内容大小...");
            
            // 限制处理内容大小，避免内存溢出
            String content = entry.getContent();
            if (content.length() > 5000) {
                log.info("内容过长，截取前5000字符进行处理");
                content = content.substring(0, 5000);
            }
            
            // 1. 创建知识条目节点
            KnowledgeNode entryNode = KnowledgeNode.builder()
                    .name(entry.getTitle())
                    .type("ENTRY")
                    .definition(content.substring(0, Math.min(200, content.length())) + "...")
                    .knowledgeEntryId(entry.getId())
                    .build();
            
            entryNode = knowledgeGraphService.addKnowledgeNode(entryNode);
            
            // 2. 提取关键概念
            List<String> concepts = nlpService.extractConcepts(content, 10);
            
            // 3. 为每个概念创建节点
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
            
            try {
                // 4. 提取实体关系 - 使用更轻量级的方法，避免内存溢出
                List<NlpService.Relationship> relationships = nlpService.extractRelationships(content);
                
                // 5. 为每个关系创建节点和边
                int relationCount = 0;
                for (NlpService.Relationship rel : relationships) {
                    // 限制关系数量
                    if (relationCount >= 20) {
                        log.info("关系数量已达到限制，跳过剩余关系");
                        break;
                    }
                    
                    String sourceEntity = rel.getSourceEntity();
                    String targetEntity = rel.getTargetEntity();
                    
                    // 检查源实体节点是否存在
                    KnowledgeNode sourceNode = knowledgeGraphService.findNodeByName(sourceEntity);
                    if (sourceNode == null) {
                        sourceNode = KnowledgeNode.builder()
                                .name(sourceEntity)
                                .type("ENTITY")
                                .definition("从" + entry.getTitle() + "中提取的实体")
                                .knowledgeEntryId(entry.getId())
                                .build();
                        
                        sourceNode = knowledgeGraphService.addKnowledgeNode(sourceNode);
                    }
                    
                    // 检查目标实体节点是否存在
                    KnowledgeNode targetNode = knowledgeGraphService.findNodeByName(targetEntity);
                    if (targetNode == null) {
                        targetNode = KnowledgeNode.builder()
                                .name(targetEntity)
                                .type("ENTITY")
                                .definition("从" + entry.getTitle() + "中提取的实体")
                                .knowledgeEntryId(entry.getId())
                                .build();
                        
                        targetNode = knowledgeGraphService.addKnowledgeNode(targetNode);
                    }
                    
                    // 添加关系
                    knowledgeGraphService.addRelationship(
                            sourceNode.getId().toString(),
                            targetNode.getId().toString(),
                            rel.getRelationType(),
                            0.8);
                    
                    relationCount++;
                }
            } catch (Exception e) {
                log.error("关系提取处理失败，但继续处理分类节点: {}", e.getMessage());
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
            
            log.info("知识图谱构建完成");
        } catch (Exception e) {
            log.error("知识图谱构建过程中发生异常: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public int batchAddKnowledgeEntries(List<KnowledgeEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (KnowledgeEntry entry : entries) {
            if (addKnowledgeEntry(entry)) {
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    public List<TextSegment> semanticSearch(String query, int maxResults) {
        try {
            log.info("开始语义搜索，查询: {}, 最大结果数: {}", query, maxResults);
            
            // 将查询文本转换为嵌入向量
            log.info("生成查询的嵌入向量");
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 使用新版API的搜索方法
            log.info("执行向量相似度搜索");
            dev.langchain4j.store.embedding.EmbeddingSearchRequest request = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(0.0) // 不设置相似度阈值
                    .build();
            
            dev.langchain4j.store.embedding.EmbeddingSearchResult<TextSegment> results = embeddingStore.search(request);

            // 转换结果并返回
            List<TextSegment> segments = results.matches().stream()
                    .map(match -> {
                        log.info("找到匹配: score={}, text={}",
                                match.score(),
                                match.embedded().text().substring(0, Math.min(50, match.embedded().text().length())) + "...");
                        return match.embedded();
                    })
                    .collect(Collectors.toList());
            
            log.info("语义搜索完成，找到 {} 个结果", segments.size());
            return segments;
        } catch (Exception e) {
            log.error("语义检索失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<KnowledgeEntry> keywordSearch(String keyword, int maxResults) {
        try {
            return knowledgeEntryMapper.searchByKeyword(keyword, maxResults);
        } catch (Exception e) {
            log.error("关键词检索失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<TextSegment> hybridSearch(String query, int maxResults) {
        // 多线程同时执行两种检索方式
        List<KnowledgeEntry> bm25Results = keywordSearch(query, maxResults);
        List<TextSegment> semanticResults = semanticSearch(query, maxResults);
        
        // 合并结果
        Set<String> addedIds = new HashSet<>();
        List<TextSegment> mergedResults = new ArrayList<>();
        
        // 交替添加两种检索结果
        int i = 0, j = 0;
        while (mergedResults.size() < maxResults && (i < bm25Results.size() || j < semanticResults.size())) {
            // 添加语义检索结果
            if (j < semanticResults.size()) {
                TextSegment segment = semanticResults.get(j);
                // 安全地从metadata获取ID - 修复方法调用
                String id = null;
                if (segment.metadata().containsKey("id")) {
                    id = segment.metadata().getString("id");
                }
                if (id != null && !id.isEmpty() && !addedIds.contains(id)) {
                    mergedResults.add(segment);
                    addedIds.add(id);
                }
                j++;
            }
            
            // 添加BM25检索结果
            if (i < bm25Results.size()) {
                KnowledgeEntry entry = bm25Results.get(i);
                if (!addedIds.contains(String.valueOf(entry.getId()))) {
                    String textContent = entry.getTitle() + ": " + entry.getContent();
                    // 使用Map创建Metadata
                    Map<String, Object> metadataMap = new HashMap<>();
                    metadataMap.put("id", String.valueOf(entry.getId()));
                    metadataMap.put("title", entry.getTitle());
                    metadataMap.put("category", entry.getCategory());
                    Metadata metadata = Metadata.from(metadataMap);
                    TextSegment segment = TextSegment.from(textContent, metadata);
                    mergedResults.add(segment);
                    addedIds.add(String.valueOf(entry.getId()));
                }
                i++;
            }
        }
        
        return mergedResults;
    }

    @Override
    public KnowledgeEntry findByTitle(String title) {
        try {
            log.info("根据标题查找知识条目: {}", title);
            LambdaQueryWrapper<KnowledgeEntry> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(KnowledgeEntry::getTitle, title);
            return knowledgeEntryMapper.selectOne(queryWrapper);
        } catch (Exception e) {
            log.error("查找知识条目失败: {}", title, e);
            return null;
        }
    }
    
    @Override
    @Transactional
    public boolean updateKnowledgeEntry(KnowledgeEntry entry) {
        try {
            log.info("开始更新知识条目: {}", entry.getTitle());
            
            // 检查是否存在
            KnowledgeEntry existingEntry = knowledgeEntryMapper.selectById(entry.getId());
            if (existingEntry == null) {
                log.warn("要更新的知识条目不存在: id={}", entry.getId());
                return false;
            }
            
            // 更新数据库
            log.info("更新知识条目到数据库");
            entry.setUpdateTime(new Date());
            knowledgeEntryMapper.updateById(entry);
            
            // 更新向量存储
            try {
                if (existingEntry.getVectorId() != null) {
                    log.info("删除旧的向量: {}", existingEntry.getVectorId());
                    // 如果向量存储支持删除，则删除旧向量
                    // 注意：部分向量存储可能不支持删除操作
                    // embeddingStore.delete(existingEntry.getVectorId());
                }
                
                // 生成新的向量
                log.info("开始生成更新后知识条目的嵌入向量");
                String textToEmbed = entry.getTitle() + ": " + entry.getContent();
                Embedding embedding = embeddingModel.embed(textToEmbed).content();
                
                // 创建元数据
                Map<String, Object> metadataMap = new HashMap<>();
                metadataMap.put("id", String.valueOf(entry.getId()));
                metadataMap.put("title", entry.getTitle());
                metadataMap.put("category", entry.getCategory());
                Metadata metadata = Metadata.from(metadataMap);
                TextSegment segment = TextSegment.from(textToEmbed, metadata);
                
                // 添加新向量
                String vectorId = embeddingStore.add(embedding, segment);
                
                // 更新vectorId
                log.info("更新知识条目的向量ID: {}", vectorId);
                entry.setVectorId(vectorId);
                knowledgeEntryMapper.updateById(entry);
                
                log.info("知识条目更新成功: {}, 新向量ID: {}", entry.getTitle(), vectorId);
            } catch (Exception e) {
                log.error("更新向量处理异常", e);
                // 虽然向量处理失败，但数据已更新到数据库，所以不抛出异常
            }
            
            return true;
        } catch (Exception e) {
            log.error("更新知识条目失败", e);
            return false;
        }
    }
} 