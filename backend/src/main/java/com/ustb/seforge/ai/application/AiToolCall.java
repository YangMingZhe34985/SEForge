package com.ustb.seforge.ai.application;

public record AiToolCall(String name, Status status, long latencyMs, String errorCode) {
    public AiToolCall {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("tool name is required");
        if (status == null) throw new IllegalArgumentException("tool status is required");
        if (latencyMs < 0) throw new IllegalArgumentException("tool latency must be non-negative");
    }

    public static AiToolCall succeeded(String name, long latencyMs) {
        return new AiToolCall(name, Status.SUCCEEDED, latencyMs, null);
    }

    public static AiToolCall failed(String name, long latencyMs, Throwable error) {
        return new AiToolCall(name, Status.FAILED, latencyMs,
                error == null ? "TOOL_ERROR" : error.getClass().getSimpleName());
    }

    public enum Status {
        SUCCEEDED,
        FAILED
    }
}
