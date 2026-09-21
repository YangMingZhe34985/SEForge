package com.ustb.smartse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Lombok配置类 - 用于确认Lombok正确生成代码
 * 该类本身不执行任何操作，但会在应用启动时打印日志，表明Lombok处理器正常工作
 */
@Slf4j
@Configuration
public class LombokConfig {
    
    public LombokConfig() {
        log.info("Lombok配置已加载 - 处理器工作正常");
    }
} 