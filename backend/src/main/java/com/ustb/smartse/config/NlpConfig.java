package com.ustb.smartse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * NLP配置类，统一管理NLP相关配置和资源初始化
 * 使用HanLP作为唯一NLP处理引擎，通过Maven管理依赖
 */
@Slf4j
@Configuration
public class NlpConfig {

    @Value("${nlp.memory.max-heap-mb:4096}")
    private int maxHeapMb;

    @PostConstruct
    public void init() {
        try {
            log.info("初始化NLP配置...");
            
            // 设置Java内存参数
            System.setProperty("java.memory", maxHeapMb + "m");
            
            log.info("NLP配置初始化完成，使用Maven管理的HanLP依赖");
        } catch (Exception e) {
            log.error("初始化NLP配置失败", e);
        }
    }
} 