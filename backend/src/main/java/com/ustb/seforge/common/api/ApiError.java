package com.ustb.seforge.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ustb.seforge.common.web.TraceContext;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, Object details, String traceId) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, TraceContext.currentTraceId());
    }

    public static ApiError of(String code, String message, Object details) {
        return new ApiError(code, message, details, TraceContext.currentTraceId());
    }
}
