package com.ustb.smartse.modules.knowledgebase.util;

import com.hankcs.hanlp.HanLP;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * HanLP模型数据下载器
 * 用于自动下载HanLP所需的模型数据文件
 */
@Slf4j
@Component
public class HanLPDataDownloader {
    
    /**
     * 在应用启动时初始化并下载模型
     */
    @PostConstruct
    public void init() {
        try {
            log.info("开始检查并下载HanLP模型数据...");
            
            // 设置模型根目录（可选，默认为当前目录下的hanlp/data）
            // System.setProperty("hanlp.root", "./hanlp");
            
            // 关闭词性显示，减少内存占用
            HanLP.Config.ShowTermNature = false;
            
            // 触发模型下载
            String result = HanLP.segment("自动触发模型下载").toString();
            log.info("HanLP模型初始化完成: {}", result);
        } catch (Exception e) {
            log.error("HanLP模型数据下载失败: {}", e.getMessage(), e);
        }
    }
} 