package com.ustb.seforge.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.domain.AiTrace;
import com.ustb.seforge.ai.repository.AiTraceRepository;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiTraceService {
    private final AiTraceRepository traces;
    private final ObjectMapper objectMapper;

    public AiTraceService(AiTraceRepository traces, ObjectMapper objectMapper) {
        this.traces = traces;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiTrace begin(AiRequest request, String provider, String model) {
        return traces.save(new AiTrace(request, provider, model, serializeToolCalls(request)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long traceId, Integer inputTokens, Integer outputTokens, Duration latency) {
        traces.findById(traceId).ifPresent(trace -> trace.succeed(inputTokens, outputTokens, latency));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long traceId, Integer inputTokens, Integer outputTokens, Duration latency,
                        List<AiToolCall> toolCalls) {
        traces.findById(traceId).ifPresent(trace -> {
            trace.updateToolCalls(serializeToolCalls(toolCalls));
            trace.succeed(inputTokens, outputTokens, latency);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long traceId, Throwable error, Duration latency) {
        traces.findById(traceId).ifPresent(trace -> trace.fail(error, latency));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long traceId, Throwable error, Duration latency, List<AiToolCall> toolCalls) {
        traces.findById(traceId).ifPresent(trace -> {
            trace.updateToolCalls(serializeToolCalls(toolCalls));
            trace.fail(error, latency);
        });
    }

    private String serializeToolCalls(AiRequest request) {
        return serializeToolCalls(request.toolCalls());
    }

    private String serializeToolCalls(List<AiToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(toolCalls);
        } catch (JsonProcessingException ignored) {
            return "[]";
        }
    }
}
