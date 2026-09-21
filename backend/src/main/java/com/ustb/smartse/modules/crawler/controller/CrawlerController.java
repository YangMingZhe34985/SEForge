package com.ustb.smartse.modules.crawler.controller;

import com.ustb.smartse.common.api.ApiResult;
import com.ustb.smartse.modules.crawler.config.CrawlerConfig;
import com.ustb.smartse.modules.crawler.entity.CrawledDocument;
import com.ustb.smartse.modules.crawler.service.CrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 爬虫控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {
    
    @Autowired
    private CrawlerService crawlerService;
    
    @Autowired
    private CrawlerConfig crawlerConfig;
    
    /**
     * 获取爬虫目标列表
     */
    @GetMapping("/targets")
    public ApiResult<List<Map<String, Object>>> getTargets() {
        try {
            List<Map<String, Object>> targets = crawlerConfig.getTargets().stream()
                    .map(target -> Map.of(
                            "name", target.getName(),
                            "targetType", target.getTargetType(),
                            "category", target.getCategory(),
                            "startUrls", target.getStartUrls())
                    )
                    .collect(Collectors.toList());
            
            return ApiResult.success(targets);
        } catch (Exception e) {
            log.error("获取爬虫目标失败", e);
            return ApiResult.error("获取爬虫目标失败: " + e.getMessage());
        }
    }
    
    /**
     * 开始爬取目标
     */
    @PostMapping("/start")
    public ApiResult<String> startCrawl(@RequestParam String targetName) {
        try {
            String taskId = crawlerService.startCrawl(targetName);
            return ApiResult.success(taskId);
        } catch (Exception e) {
            log.error("启动爬虫任务失败", e);
            return ApiResult.error("启动爬虫任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 爬取指定URL的PDF
     */
    @PostMapping("/pdf")
    public ApiResult<Boolean> crawlPdf(@RequestParam String url, @RequestParam String category) {
        try {
            boolean result = crawlerService.crawlPdf(url, category);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("爬取PDF失败", e);
            return ApiResult.error("爬取PDF失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量爬取PDF
     */
    @PostMapping("/pdf/batch")
    public ApiResult<Integer> batchCrawlPdf(@RequestParam String urls, @RequestParam String category) {
        try {
            List<String> urlList = Arrays.asList(urls.split("\\s*[,\\n]\\s*"));
            int count = crawlerService.batchCrawlPdf(urlList, category);
            return ApiResult.success(count);
        } catch (Exception e) {
            log.error("批量爬取PDF失败", e);
            return ApiResult.error("批量爬取PDF失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取爬虫状态
     */
    @GetMapping("/status")
    public ApiResult<Map<String, Object>> getCrawlStatus() {
        try {
            Map<String, Object> status = crawlerService.getCrawlStatus();
            return ApiResult.success(status);
        } catch (Exception e) {
            log.error("获取爬虫状态失败", e);
            return ApiResult.error("获取爬虫状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理爬取文档
     */
    @PostMapping("/process")
    public ApiResult<Integer> processDocuments(@RequestParam(defaultValue = "20") int batchSize) {
        try {
            int count = crawlerService.processDocuments(batchSize);
            return ApiResult.success(count);
        } catch (Exception e) {
            log.error("处理文档失败", e);
            return ApiResult.error("处理文档失败: " + e.getMessage());
        }
    }
    
    /**
     * 导入到知识库
     */
    @PostMapping("/import")
    public ApiResult<Integer> importToKnowledgeBase(@RequestParam(defaultValue = "20") int batchSize) {
        try {
            int count = crawlerService.importToKnowledgeBase(batchSize);
            return ApiResult.success(count);
        } catch (Exception e) {
            log.error("导入知识库失败", e);
            return ApiResult.error("导入知识库失败: " + e.getMessage());
        }
    }
    
    /**
     * 分页查询爬取文档
     */
    @GetMapping("/documents")
    public ApiResult<List<CrawledDocument>> findPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        try {
            List<CrawledDocument> documents = crawlerService.findPage(page, size, category);
            return ApiResult.success(documents);
        } catch (Exception e) {
            log.error("查询爬取文档失败", e);
            return ApiResult.error("查询爬取文档失败: " + e.getMessage());
        }
    }
    
    /**
     * 查看爬取文档详情
     */
    @GetMapping("/documents/{id}")
    public ApiResult<CrawledDocument> findById(@PathVariable Long id) {
        try {
            CrawledDocument document = crawlerService.findById(id);
            if (document == null) {
                return ApiResult.error("文档不存在");
            }
            return ApiResult.success(document);
        } catch (Exception e) {
            log.error("查看爬取文档详情失败", e);
            return ApiResult.error("查看爬取文档详情失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理过期数据
     */
    @DeleteMapping("/cleanup")
    public ApiResult<Integer> cleanupData(@RequestParam(defaultValue = "30") int days) {
        try {
            int count = crawlerService.cleanupData(days);
            return ApiResult.success(count);
        } catch (Exception e) {
            log.error("清理数据失败", e);
            return ApiResult.error("清理数据失败: " + e.getMessage());
        }
    }
} 