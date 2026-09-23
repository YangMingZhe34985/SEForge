package com.ustb.seforge.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncExecutionConfig {

    @Bean(name = "applicationTaskExecutor")
    public TaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int processors = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(Math.max(2, Math.min(processors, 8)));
        executor.setMaxPoolSize(Math.max(8, Math.min(processors * 2, 32)));
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("seforge-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(task -> {
            Map<String, String> callerContext = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (callerContext == null) MDC.clear();
                    else MDC.setContextMap(callerContext);
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });
        return executor;
    }
}
