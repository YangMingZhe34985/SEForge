package com.ustb.smartse.modules.crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 爬虫配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "crawler")
public class CrawlerConfig {
    
    /**
     * 爬虫线程数
     */
    private int threadNum = 5;
    
    /**
     * 爬虫延迟时间(毫秒)
     */
    private int sleepTime = 1000;
    
    /**
     * 爬虫重试次数
     */
    private int retryTimes = 3;
    
    /**
     * 每个站点爬取的最大页面数
     */
    private int maxPageCount = 100;
    
    /**
     * 爬虫超时时间(毫秒)
     */
    private int timeout = 10000;
    
    /**
     * 用户代理
     */
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    
    /**
     * 预定义的爬虫目标
     */
    private List<CrawlTarget> targets = new ArrayList<>();
    
    // 手动添加getter/setter方法以确保可访问性
    
    public int getThreadNum() {
        return threadNum;
    }
    
    public void setThreadNum(int threadNum) {
        this.threadNum = threadNum;
    }
    
    public int getSleepTime() {
        return sleepTime;
    }
    
    public void setSleepTime(int sleepTime) {
        this.sleepTime = sleepTime;
    }
    
    public int getRetryTimes() {
        return retryTimes;
    }
    
    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }
    
    public int getMaxPageCount() {
        return maxPageCount;
    }
    
    public void setMaxPageCount(int maxPageCount) {
        this.maxPageCount = maxPageCount;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public List<CrawlTarget> getTargets() {
        return targets;
    }
    
    public void setTargets(List<CrawlTarget> targets) {
        this.targets = targets;
    }
    
    /**
     * 爬虫目标配置
     */
    @Data
    public static class CrawlTarget {
        /**
         * 目标名称
         */
        private String name;
        
        /**
         * 起始URL
         */
        private List<String> startUrls;
        
        /**
         * URL正则表达式，用于匹配需要爬取的URL
         */
        private String urlPattern;
        
        /**
         * 目标类型：网页、PDF等
         */
        private String targetType;
        
        /**
         * 目标分类
         */
        private String category;
        
        /**
         * 内容提取选择器
         */
        private Map<String, String> selectors;
        
        // 手动添加getter/setter方法以确保可访问性
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public List<String> getStartUrls() {
            return startUrls;
        }
        
        public void setStartUrls(List<String> startUrls) {
            this.startUrls = startUrls;
        }
        
        public String getUrlPattern() {
            return urlPattern;
        }
        
        public void setUrlPattern(String urlPattern) {
            this.urlPattern = urlPattern;
        }
        
        public String getTargetType() {
            return targetType;
        }
        
        public void setTargetType(String targetType) {
            this.targetType = targetType;
        }
        
        public String getCategory() {
            return category;
        }
        
        public void setCategory(String category) {
            this.category = category;
        }
        
        public Map<String, String> getSelectors() {
            return selectors;
        }
        
        public void setSelectors(Map<String, String> selectors) {
            this.selectors = selectors;
        }
    }
} 