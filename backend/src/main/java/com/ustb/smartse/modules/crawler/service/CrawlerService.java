package com.ustb.smartse.modules.crawler.service;

import com.ustb.smartse.modules.crawler.config.CrawlerConfig;
import com.ustb.smartse.modules.crawler.entity.CrawledDocument;
import com.ustb.smartse.modules.crawler.mapper.CrawledDocumentMapper;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 爬虫服务接口
 */
public interface CrawlerService {
    
    /**
     * 开始爬取指定目标
     * @param targetName 目标名称
     * @return 爬取任务ID
     */
    String startCrawl(String targetName);
    
    /**
     * 通过配置爬取网页
     * @param target 爬取目标配置
     * @return 爬取数量
     */
    int crawlWebpages(CrawlerConfig.CrawlTarget target);
    
    /**
     * 爬取指定URL的PDF文件
     * @param url PDF文件URL
     * @param category 分类
     * @return 是否成功
     */
    boolean crawlPdf(String url, String category);
    
    /**
     * 批量爬取PDF文件
     * @param urls PDF文件URL列表
     * @param category 分类
     * @return 成功数量
     */
    int batchCrawlPdf(List<String> urls, String category);
    
    /**
     * 从本地导入PDF文件
     * @param file PDF文件
     * @param category 分类
     * @return 是否成功
     */
    boolean importPdfFromLocal(File file, String category);
    
    /**
     * 获取爬取状态
     * @return 爬取状态统计
     */
    Map<String, Object> getCrawlStatus();
    
    /**
     * 加工处理爬取的文档
     * @param batchSize 批处理大小
     * @return 处理数量
     */
    int processDocuments(int batchSize);
    
    /**
     * 将处理后的文档导入知识库
     * @param batchSize 批处理大小
     * @return 导入数量
     */
    int importToKnowledgeBase(int batchSize);
    
    /**
     * 根据ID查询爬取文档
     * @param id 文档ID
     * @return 爬取文档
     */
    CrawledDocument findById(Long id);
    
    /**
     * 分页查询爬取文档
     * @param page 页码
     * @param size 每页大小
     * @param category 分类
     * @return 文档列表
     */
    List<CrawledDocument> findPage(int page, int size, String category);
    
    /**
     * 清理爬取数据
     * @param days 保留最近几天的数据
     * @return 清理数量
     */
    int cleanupData(int days);
} 