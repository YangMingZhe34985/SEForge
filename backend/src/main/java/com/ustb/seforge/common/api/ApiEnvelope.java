package com.ustb.seforge.common.api;

import com.ustb.seforge.common.web.TraceContext;

public record ApiEnvelope<T>(String code, String message, T data, String traceId) {

    public static <T> ApiEnvelope<T> success(T data) {
        return new ApiEnvelope<>("OK", "success", data, TraceContext.currentTraceId());
    }

    public static <T> ApiEnvelope<T> success(String message, T data) {
        return new ApiEnvelope<>("OK", message, data, TraceContext.currentTraceId());
    }
}
