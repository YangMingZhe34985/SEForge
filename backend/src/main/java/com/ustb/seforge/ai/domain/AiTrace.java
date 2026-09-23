package com.ustb.seforge.ai.domain;

import com.ustb.seforge.ai.application.AiRequest;
import com.ustb.seforge.common.persistence.BaseEntity;
import com.ustb.seforge.common.web.TraceContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_trace", indexes = {
        @Index(name = "idx_ai_trace_course_created", columnList = "course_id,created_at"),
        @Index(name = "idx_ai_trace_user_created", columnList = "user_id,created_at")
})
public class AiTrace extends BaseEntity {
    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false, length = 32)
    private String capability;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(name = "model_name", nullable = false, length = 128)
    private String model;

    @Column(name = "prompt_name", length = 128)
    private String promptName;

    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AiTraceStatus status;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "tool_calls", columnDefinition = "json")
    private String toolCallsJson;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AiTrace() {
    }

    public AiTrace(AiRequest request, String provider, String model, String toolCallsJson) {
        requestId = UUID.randomUUID().toString();
        traceId = TraceContext.getOrCreate();
        userId = request.userId();
        courseId = request.courseId();
        capability = request.capability().name();
        this.provider = provider;
        this.model = model;
        int separator = request.promptVersion().indexOf(':');
        promptName = separator > 0 ? request.promptVersion().substring(0, separator) : request.promptVersion();
        promptVersion = request.promptVersion();
        this.toolCallsJson = toolCallsJson;
        status = AiTraceStatus.STARTED;
        startedAt = Instant.now();
    }

    public void succeed(Integer inputTokens, Integer outputTokens, Duration latency) {
        status = AiTraceStatus.SUCCEEDED;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        latencyMs = latency.toMillis();
        completedAt = Instant.now();
    }

    public void updateToolCalls(String toolCallsJson) {
        this.toolCallsJson = toolCallsJson;
    }

    public void fail(Throwable error, Duration latency) {
        status = AiTraceStatus.FAILED;
        latencyMs = latency.toMillis();
        completedAt = Instant.now();
        errorCode = error == null ? "AI_PROVIDER_ERROR" : error.getClass().getSimpleName();
    }

    public String getRequestId() { return requestId; }
    public Long getUserId() { return userId; }
    public Long getCourseId() { return courseId; }
    public String getCapability() { return capability; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getPromptVersion() { return promptVersion; }
    public AiTraceStatus getStatus() { return status; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public Long getLatencyMs() { return latencyMs; }
    public String getToolCallsJson() { return toolCallsJson; }
    public String getErrorCode() { return errorCode; }
}
