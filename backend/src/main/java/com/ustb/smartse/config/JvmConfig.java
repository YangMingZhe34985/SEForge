package com.ustb.smartse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;

/**
 * JVM配置类，用于设置和监控JVM参数
 */
@Slf4j
@Configuration
public class JvmConfig {

    @PostConstruct
    public void init() {
        configureJvmParameters();
        logJvmInfo();
    }
    
    /**
     * 配置JVM参数
     */
    private void configureJvmParameters() {
        // 设置系统属性
        System.setProperty("file.encoding", "UTF-8");
        
        // 设置GC相关参数（仅记录，实际需要在JVM启动参数中设置）
        log.info("建议的JVM参数: -Xms1024m -Xmx2048m -XX:MaxMetaspaceSize=512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError");
    }
    
    /**
     * 记录JVM信息
     */
    private void logJvmInfo() {
        // 获取JVM内存信息
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        log.info("堆内存初始大小: {}MB", memoryMXBean.getHeapMemoryUsage().getInit() / (1024 * 1024));
        log.info("堆内存最大大小: {}MB", memoryMXBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
        log.info("非堆内存初始大小: {}MB", memoryMXBean.getNonHeapMemoryUsage().getInit() / (1024 * 1024));
        log.info("非堆内存最大大小: {}MB", memoryMXBean.getNonHeapMemoryUsage().getMax() / (1024 * 1024));
        
        // 获取内存池信息
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : memoryPoolMXBeans) {
            log.info("内存池: {}, 类型: {}, 使用量: {}MB, 最大: {}MB", 
                    pool.getName(), 
                    pool.getType(),
                    pool.getUsage().getUsed() / (1024 * 1024),
                    pool.getUsage().getMax() / (1024 * 1024));
        }
        
        // 获取JVM参数
        List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        log.info("JVM启动参数: {}", inputArguments);
    }
} 