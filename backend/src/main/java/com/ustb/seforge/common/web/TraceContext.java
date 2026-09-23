package com.ustb.seforge.common.web;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceContext {
    public static final String TRACE_ID = "traceId";

    private TraceContext() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID);
        return traceId == null || traceId.isBlank() ? "unavailable" : traceId;
    }

    public static String getOrCreate() {
        String traceId = MDC.get(TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return traceId;
    }
}
