package com.ustb.smartse.modules.knowledgebase.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * NLP配置类，用于配置GLiNER和GLiREL模型的路径
 */
@Configuration
@PropertySource("classpath:application.yml")
@ConfigurationProperties(prefix = "nlp.gli")
public class GliNlpConfig {
    
    private String glinerModelPath = "models/gliner";
    private String glirelModelPath = "models/glirel";
    
    public String getGlinerModelPath() {
        return glinerModelPath;
    }
    
    public void setGlinerModelPath(String glinerModelPath) {
        this.glinerModelPath = glinerModelPath;
    }
    
    public String getGlirelModelPath() {
        return glirelModelPath;
    }
    
    public void setGlirelModelPath(String glirelModelPath) {
        this.glirelModelPath = glirelModelPath;
    }
} 