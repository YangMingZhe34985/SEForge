package com.ustb.seforge.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ustb.seforge.config.AsyncExecutionConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncExecutionConfigTest {

    @Test
    void propagatesTheCallerTraceAndClearsItAfterExecution() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncExecutionConfig()
                .applicationTaskExecutor();
        executor.initialize();
        try {
            MDC.put(TraceContext.TRACE_ID, "request-trace-42");
            CompletableFuture<String> propagated = new CompletableFuture<>();
            executor.execute(() -> propagated.complete(TraceContext.currentTraceId()));
            assertThat(propagated.get(5, TimeUnit.SECONDS)).isEqualTo("request-trace-42");

            MDC.clear();
            CompletableFuture<String> cleared = new CompletableFuture<>();
            executor.execute(() -> cleared.complete(TraceContext.currentTraceId()));
            assertThat(cleared.get(5, TimeUnit.SECONDS)).isEqualTo("unavailable");
        } finally {
            MDC.clear();
            executor.shutdown();
        }
    }
}
