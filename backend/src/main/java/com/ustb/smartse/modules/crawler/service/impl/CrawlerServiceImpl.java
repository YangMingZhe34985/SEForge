package com.ustb.smartse.modules.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ustb.smartse.modules.crawler.config.CrawlerConfig;
import com.ustb.smartse.modules.crawler.entity.CrawledDocument;
import com.ustb.smartse.modules.crawler.mapper.CrawledDocumentMapper;
import com.ustb.smartse.modules.crawler.service.CrawlerService;
import com.ustb.smartse.modules.crawler.util.HtmlToMarkdownConverter;
import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Html;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 爬虫服务实现类
 */
@Slf4j
@Service
public class CrawlerServiceImpl extends ServiceImpl<CrawledDocumentMapper, CrawledDocument> implements CrawlerService {

    @Autowired
    private CrawlerConfig crawlerConfig;
    
    @Autowired
    private CrawledDocumentMapper crawledDocumentMapper;
    
    @Autowired
    private KnowledgeService knowledgeService;
    
    @Autowired
    private HtmlToMarkdownConverter htmlToMarkdownConverter;
    
    // 爬虫状态记录
    private final Map<String, CrawlTask> crawlTasks = new ConcurrentHashMap<>();

    /**
     * 爬虫任务记录
     */
    static class CrawlTask {
        String id;
        String targetName;
        LocalDateTime startTime;
        LocalDateTime endTime;
        String status; // RUNNING, COMPLETED, FAILED
        int pageCount;
        int successCount;
        int failCount;
        String message;
        
        public CrawlTask(String id, String targetName) {
            this.id = id;
            this.targetName = targetName;
            this.startTime = LocalDateTime.now();
            this.status = "RUNNING";
            this.pageCount = 0;
            this.successCount = 0;
            this.failCount = 0;
        }
    }

    @Override
    public String startCrawl(String targetName) {
        Optional<CrawlerConfig.CrawlTarget> targetOpt = crawlerConfig.getTargets().stream()
                .filter(t -> t.getName().equals(targetName))
                .findFirst();
        
        if (targetOpt.isEmpty()) {
            throw new IllegalArgumentException("未找到目标配置: " + targetName);
        }
        
        CrawlerConfig.CrawlTarget target = targetOpt.get();
        String taskId = UUID.randomUUID().toString();
        CrawlTask task = new CrawlTask(taskId, targetName);
        crawlTasks.put(taskId, task);
        
        // 启动爬虫线程
        new Thread(() -> {
            try {
                int count;
                if ("webpage".equalsIgnoreCase(target.getTargetType())) {
                    count = crawlWebpages(target);
                } else if ("pdf".equalsIgnoreCase(target.getTargetType())) {
                    count = batchCrawlPdf(target.getStartUrls(), target.getCategory());
                } else {
                    throw new IllegalArgumentException("不支持的目标类型: " + target.getTargetType());
                }
                
                task.endTime = LocalDateTime.now();
                task.status = "COMPLETED";
                task.successCount = count;
                task.message = "成功爬取 " + count + " 个文档";
            } catch (Exception e) {
                log.error("爬取任务失败: " + targetName, e);
                task.endTime = LocalDateTime.now();
                task.status = "FAILED";
                task.message = e.getMessage();
            }
        }).start();
        
        return taskId;
    }

    @Override
    public int crawlWebpages(CrawlerConfig.CrawlTarget target) {
        final AtomicInteger count = new AtomicInteger(0);
        final int maxPages = crawlerConfig.getMaxPageCount();
        
        try {
            Spider.create(new PageProcessor() {
                private Site site = Site.me()
                        .setUserAgent(crawlerConfig.getUserAgent())
                        .setRetryTimes(crawlerConfig.getRetryTimes())
                        .setSleepTime(crawlerConfig.getSleepTime())
                        .setTimeOut(crawlerConfig.getTimeout());
                
                @Override
                public void process(Page page) {
                    // 检查是否达到最大页面数
                    if (count.get() >= maxPages) {
                        page.setSkip(true);
                        return;
                    }
                    
                    // 添加新的URL到爬取队列
                    String urlPattern = target.getUrlPattern();
                    if (StringUtils.isNotBlank(urlPattern)) {
                        page.addTargetRequests(
                                page.getHtml().links().regex(urlPattern).all()
                        );
                    } else {
                        page.addTargetRequests(page.getHtml().links().all());
                    }
                    
                    // 提取内容
                    Html html = page.getHtml();
                    String title = "";
                    String content = "";
                    
                    // 使用配置的选择器提取内容
                    Map<String, String> selectors = target.getSelectors();
                    if (selectors != null && !selectors.isEmpty()) {
                        if (selectors.containsKey("title")) {
                            title = html.xpath(selectors.get("title")).get();
                        }
                        if (selectors.containsKey("content")) {
                            content = html.xpath(selectors.get("content")).get();
                        }
                    }
                    
                    // 如果没有配置选择器或提取失败，使用默认逻辑
                    if (StringUtils.isBlank(title)) {
                        title = html.xpath("//title/text()").get();
                    }
                    if (StringUtils.isBlank(content)) {
                        content = html.xpath("//article").get();
                        if (StringUtils.isBlank(content)) {
                            content = html.xpath("//div[@class='content']").get();
                        }
                        if (StringUtils.isBlank(content)) {
                            content = html.xpath("//div[@id='content']").get();
                        }
                        if (StringUtils.isBlank(content)) {
                            content = html.xpath("//body").get();
                        }
                    }
                    
                    // 保存爬取结果
                    if (StringUtils.isNotBlank(title) && StringUtils.isNotBlank(content)) {
                        String url = page.getUrl().get();
                        saveWebpage(title, content, url, target.getCategory());
                        count.incrementAndGet();
                    }
                }
                
                @Override
                public Site getSite() {
                    return site;
                }
            })
            .addUrl(target.getStartUrls().toArray(new String[0]))
            .thread(crawlerConfig.getThreadNum())
            .run();
        } catch (Exception e) {
            log.error("爬取网页失败", e);
        }
        
        return count.get();
    }
    
    /**
     * 保存网页到数据库
     */
    private void saveWebpage(String title, String content, String url, String category) {
        try {
            // 检查URL是否已存在
            if (crawledDocumentMapper.countByUrl(url) > 0) {
                return;
            }
            
            // 转换HTML为Markdown
            String markdown = htmlToMarkdownConverter.convert(content);
            
            // 保存到数据库
            CrawledDocument document = CrawledDocument.builder()
                    .title(title)
                    .content(markdown)
                    .url(url)
                    .source(extractDomain(url))
                    .documentType("html")
                    .category(category)
                    .processStatus(0)
                    .importStatus(0)
                    .crawlTime(LocalDateTime.now())
                    .build();
            
            crawledDocumentMapper.insert(document);
        } catch (Exception e) {
            log.error("保存网页失败: " + url, e);
        }
    }
    
    /**
     * 提取URL域名
     */
    private String extractDomain(String url) {
        try {
            URL u = new URL(url);
            return u.getHost();
        } catch (Exception e) {
            return url;
        }
    }

    @Override
    public boolean crawlPdf(String url, String category) {
        try {
            // 检查URL是否已存在
            if (crawledDocumentMapper.countByUrl(url) > 0) {
                return false;
            }
            
            // 下载PDF
            URL pdfUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) pdfUrl.openConnection();
            connection.setRequestProperty("User-Agent", crawlerConfig.getUserAgent());
            
            try (InputStream inputStream = connection.getInputStream()) {
                // 解析PDF
                PDDocument document = PDDocument.load(inputStream);
                PDFTextStripper stripper = new PDFTextStripper();
                String content = stripper.getText(document);
                document.close();
                
                // 提取标题（使用文件名作为标题）
                String title = url.substring(url.lastIndexOf('/') + 1);
                if (title.contains("?")) {
                    title = title.substring(0, title.indexOf('?'));
                }
                if (title.toLowerCase().endsWith(".pdf")) {
                    title = title.substring(0, title.length() - 4);
                }
                
                // 保存到数据库
                CrawledDocument crawledDocument = CrawledDocument.builder()
                        .title(title)
                        .content(content)
                        .url(url)
                        .source(extractDomain(url))
                        .documentType("pdf")
                        .category(category)
                        .processStatus(0)
                        .importStatus(0)
                        .crawlTime(LocalDateTime.now())
                        .build();
                
                crawledDocumentMapper.insert(crawledDocument);
                return true;
            }
        } catch (Exception e) {
            log.error("爬取PDF失败: " + url, e);
            return false;
        }
    }

    @Override
    public int batchCrawlPdf(List<String> urls, String category) {
        int successCount = 0;
        for (String url : urls) {
            if (crawlPdf(url, category)) {
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    public boolean importPdfFromLocal(File file, String category) {
        try {
            // 解析PDF
            PDDocument document = PDDocument.load(file);
            PDFTextStripper stripper = new PDFTextStripper();
            String content = stripper.getText(document);
            document.close();
            
            // 使用文件名作为标题
            String title = file.getName();
            if (title.toLowerCase().endsWith(".pdf")) {
                title = title.substring(0, title.length() - 4);
            }
            
            // 保存到数据库
            CrawledDocument crawledDocument = CrawledDocument.builder()
                    .title(title)
                    .content(content)
                    .url("file://" + file.getAbsolutePath())
                    .source("local")
                    .documentType("pdf")
                    .category(category)
                    .processStatus(0)
                    .importStatus(0)
                    .crawlTime(LocalDateTime.now())
                    .build();
            
            crawledDocumentMapper.insert(crawledDocument);
            return true;
        } catch (Exception e) {
            log.error("导入本地PDF失败: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getCrawlStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 任务状态
        status.put("tasks", crawlTasks);
        
        // 统计信息
        try {
            // 总文档数
            long totalCount = count();
            status.put("totalDocuments", totalCount);
            
            // 按类型统计
            Map<String, Long> typeStats = list().stream()
                    .collect(Collectors.groupingBy(CrawledDocument::getDocumentType, Collectors.counting()));
            status.put("byType", typeStats);
            
            // 按分类统计
            List<CrawledDocumentMapper.CrawlerStatistics> categoryStats = crawledDocumentMapper.countByCategory();
            status.put("byCategory", categoryStats);
            
            // 处理状态统计
            Map<Integer, Long> processStats = list().stream()
                    .collect(Collectors.groupingBy(CrawledDocument::getProcessStatus, Collectors.counting()));
            status.put("byProcessStatus", processStats);
            
        } catch (Exception e) {
            log.error("获取爬虫状态失败", e);
            status.put("error", e.getMessage());
        }
        
        return status;
    }

    @Override
    @Transactional
    public int processDocuments(int batchSize) {
        // 获取待处理的文档
        List<CrawledDocument> documents = crawledDocumentMapper.findPendingDocuments(batchSize);
        int processedCount = 0;
        
        for (CrawledDocument document : documents) {
            try {
                // 更新为处理中状态
                crawledDocumentMapper.updateProcessStatus(document.getId(), 1);
                
                // 处理内容（这里可以添加更多处理逻辑，如分段、清洗等）
                String processedContent = document.getContent();
                
                // 如果是HTML，转换为Markdown
                if ("html".equals(document.getDocumentType()) && document.getContent().contains("<")) {
                    processedContent = htmlToMarkdownConverter.convert(document.getContent());
                }
                
                // 分割大文档（超过5000字符的分割为多个段落）
                List<String> segments = splitContent(processedContent, 5000);
                
                // 更新文档内容（如果只有一个段落，直接更新；否则更新第一个段落，其余创建新文档）
                if (segments.size() == 1) {
                    document.setContent(segments.get(0));
                    document.setProcessStatus(2); // 已处理
                    updateById(document);
                } else {
                    // 更新第一个段落
                    document.setContent(segments.get(0));
                    document.setProcessStatus(2); // 已处理
                    updateById(document);
                    
                    // 创建其余段落的文档
                    for (int i = 1; i < segments.size(); i++) {
                        CrawledDocument segment = CrawledDocument.builder()
                                .title(document.getTitle() + " (Part " + (i + 1) + ")")
                                .content(segments.get(i))
                                .url(document.getUrl() + "#part" + (i + 1))
                                .source(document.getSource())
                                .documentType(document.getDocumentType())
                                .category(document.getCategory())
                                .processStatus(2) // 已处理
                                .importStatus(0)
                                .crawlTime(document.getCrawlTime())
                                .processTime(LocalDateTime.now())
                                .build();
                        
                        save(segment);
                    }
                }
                
                processedCount++;
            } catch (Exception e) {
                log.error("处理文档失败: " + document.getId(), e);
                crawledDocumentMapper.updateProcessStatus(document.getId(), 3); // 处理失败
            }
        }
        
        return processedCount;
    }
    
    /**
     * 分割内容为多个段落
     * @param content 内容
     * @param maxLength 最大长度
     * @return 段落列表
     */
    private List<String> splitContent(String content, int maxLength) {
        List<String> segments = new ArrayList<>();
        
        if (content.length() <= maxLength) {
            segments.add(content);
            return segments;
        }
        
        // 按段落分割
        String[] paragraphs = content.split("\n\n");
        StringBuilder currentSegment = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            // 如果当前段落加上新段落不超过最大长度，追加
            if (currentSegment.length() + paragraph.length() + 2 <= maxLength) {
                if (currentSegment.length() > 0) {
                    currentSegment.append("\n\n");
                }
                currentSegment.append(paragraph);
            } else {
                // 如果当前段落非空，添加到结果
                if (currentSegment.length() > 0) {
                    segments.add(currentSegment.toString());
                    currentSegment = new StringBuilder();
                }
                
                // 如果单个段落超过最大长度，拆分
                if (paragraph.length() > maxLength) {
                    int start = 0;
                    while (start < paragraph.length()) {
                        int end = Math.min(start + maxLength, paragraph.length());
                        // 尝试在单词边界切分
                        if (end < paragraph.length()) {
                            int spacePos = paragraph.lastIndexOf(' ', end);
                            if (spacePos > start && (end - spacePos) < 100) { // 如果空格在合理范围内
                                end = spacePos;
                            }
                        }
                        segments.add(paragraph.substring(start, end));
                        start = end;
                    }
                } else {
                    currentSegment.append(paragraph);
                }
            }
        }
        
        // 添加最后一个段落
        if (currentSegment.length() > 0) {
            segments.add(currentSegment.toString());
        }
        
        return segments;
    }

    @Override
    @Transactional
    public int importToKnowledgeBase(int batchSize) {
        // 获取待导入的文档
        List<CrawledDocument> documents = crawledDocumentMapper.findPendingImportDocuments(batchSize);
        int importedCount = 0;
        
        for (CrawledDocument document : documents) {
            try {
                // 转换为知识条目
                KnowledgeEntry entry = KnowledgeEntry.builder()
                        .title(document.getTitle())
                        .content(document.getContent())
                        .sourceType(mapDocumentTypeToSourceType(document.getDocumentType()))
                        .sourceId("crawler_" + document.getId())
                        .category(document.getCategory())
                        .createTime(new Date())
                        .updateTime(new Date())
                        .build();
                
                // 导入知识库
                boolean success = knowledgeService.addKnowledgeEntry(entry);
                
                if (success) {
                    // 更新导入状态
                    crawledDocumentMapper.updateImportStatus(document.getId(), 1);
                    importedCount++;
                } else {
                    log.error("导入知识库失败: " + document.getId());
                }
            } catch (Exception e) {
                log.error("导入知识库失败: " + document.getId(), e);
            }
        }
        
        return importedCount;
    }
    
    /**
     * 映射文档类型到知识库源类型
     */
    private Integer mapDocumentTypeToSourceType(String documentType) {
        switch (documentType.toLowerCase()) {
            case "html":
                return 1; // 课程大纲
            case "pdf":
                return 4; // 教材PDF
            default:
                return 1;
        }
    }

    @Override
    public CrawledDocument findById(Long id) {
        return getById(id);
    }

    @Override
    public List<CrawledDocument> findPage(int page, int size, String category) {
        LambdaQueryWrapper<CrawledDocument> queryWrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.isNotBlank(category)) {
            queryWrapper.eq(CrawledDocument::getCategory, category);
        }
        
        queryWrapper.orderByDesc(CrawledDocument::getCrawlTime);
        
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CrawledDocument> pageResult = 
            page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), queryWrapper);
        return pageResult.getRecords();
    }

    @Override
    @Transactional
    public int cleanupData(int days) {
        // 计算截止日期
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        
        // 删除旧数据
        LambdaQueryWrapper<CrawledDocument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.lt(CrawledDocument::getCrawlTime, cutoffDate);
        
        long totalCount = count(queryWrapper);
        int count = totalCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalCount;
        remove(queryWrapper);
        
        return count;
    }
} 