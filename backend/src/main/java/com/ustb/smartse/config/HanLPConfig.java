package com.ustb.smartse.config;

import com.hankcs.hanlp.HanLP;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * HanLP配置类
 */
@Slf4j
@Configuration
public class HanLPConfig {

    @Value("${hanlp.root:./hanlp}")
    private String hanlpRoot;
    
    @Value("${hanlp.show-term-nature:false}")
    private boolean showTermNature;
    
    @PostConstruct
    public void init() {
        try {
            log.info("初始化HanLP配置...");
            
            // 设置HanLP根目录
            System.setProperty("hanlp.root", hanlpRoot);
            log.info("HanLP根目录设置为: {}", hanlpRoot);
            
            // 设置是否显示词性
            HanLP.Config.ShowTermNature = showTermNature;
            log.info("HanLP词性显示设置为: {}", showTermNature);
            
            log.info("HanLP配置初始化完成");
        } catch (Exception e) {
            log.error("HanLP配置初始化失败: {}", e.getMessage(), e);
        }
    }
} 