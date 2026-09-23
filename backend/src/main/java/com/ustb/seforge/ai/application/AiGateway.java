package com.ustb.seforge.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.domain.AiTrace;
import com.ustb.seforge.ai.infrastructure.AiModelEndpoint;
import com.ustb.seforge.ai.infrastructure.AiServiceFactory;
import com.ustb.seforge.ai.infrastructure.ModelRouter;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.V;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolExecution;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class AiGateway {
    private final ModelRouter router;
    private final AiTraceService traces;
    private final ObjectMapper objectMapper;
    private final AiServiceFactory serviceFactory;
    private final ServiceOutputParser outputParser = new ServiceOutputParser();

    public AiGateway(ModelRouter router, AiTraceService traces, ObjectMapper objectMapper,
                     AiServiceFactory serviceFactory) {
        this.router = router;
        this.traces = traces;
        this.objectMapper = objectMapper;
        this.serviceFactory = serviceFactory;
    }

    /**
     * Executes a synchronous AI Service with only the supplied request-scoped tools.
     * Tool objects must bind authorization context server-side; identifiers supplied by
     * model-generated arguments must never be trusted as an authorization boundary.
     */
    public AiToolsResponse completeWithTools(AiRequest request, Set<String> requiredToolNames,
                                             Object... authorizedTools) {
        if (authorizedTools == null || authorizedTools.length == 0) {
            throw new IllegalArgumentException("At least one authorized tool is required");
        }
        Set<String> required = requiredToolNames == null ? Set.of() : Set.copyOf(requiredToolNames);
        RuntimeException last = null;
        for (AiModelEndpoint endpoint : router.candidates(request.capability())) {
            beginToolAttempt(authorizedTools);
            Map<AiToolCallRecorder, Integer> recorderOffsets = recorderOffsets(authorizedTools);
            Instant startedAt = Instant.now();
            AiTrace trace = traces.begin(request, endpoint.provider(), endpoint.model());
            try {
                ToolAwareAssistant assistant = serviceFactory.create(
                        ToolAwareAssistant.class, endpoint, null, authorizedTools);
                Result<String> result = assistant.chat(normalizeSystemPrompt(request.systemPrompt()),
                        request.userPrompt());
                List<ToolExecution> executions = result.toolExecutions() == null
                        ? List.of() : List.copyOf(result.toolExecutions());
                requireSuccessfulTools(executions, required);
                List<AiToolCall> toolCalls = mergeToolCalls(request.toolCalls(), executions,
                        recordedSince(recorderOffsets));
                TokenUsage usage = result.tokenUsage();
                AiResponse response = new AiResponse(result.content(), endpoint.provider(), endpoint.model(),
                        usage == null ? null : usage.inputTokenCount(),
                        usage == null ? null : usage.outputTokenCount(), trace.getId());
                traces.succeed(trace.getId(), response.inputTokens(), response.outputTokens(),
                        Duration.between(startedAt, Instant.now()), toolCalls);
                List<AiToolExecutionResult> toolResults = executions.stream()
                        .map(execution -> new AiToolExecutionResult(execution.request().name(),
                                execution.resultObject(), execution.hasFailed()))
                        .toList();
                return new AiToolsResponse(response, toolResults);
            } catch (RuntimeException exception) {
                List<AiToolCall> toolCalls = mergeToolCalls(request.toolCalls(), List.of(),
                        recordedSince(recorderOffsets));
                traces.fail(trace.getId(), exception, Duration.between(startedAt, Instant.now()), toolCalls);
                last = exception;
            }
        }
        throw new AiUnavailableException(last == null ? "No AI model is configured" : last.getMessage(), last);
    }

    public AiResponse complete(AiRequest request) {
        RuntimeException last = null;
        for (AiModelEndpoint endpoint : router.candidates(request.capability())) {
            Instant startedAt = Instant.now();
            AiTrace trace = traces.begin(request, endpoint.provider(), endpoint.model());
            try {
                ChatResponse response = endpoint.chatModel().chat(messages(request));
                AiResponse result = response(response, endpoint, trace.getId());
                traces.succeed(trace.getId(), result.inputTokens(), result.outputTokens(),
                        Duration.between(startedAt, Instant.now()));
                return result;
            } catch (RuntimeException exception) {
                traces.fail(trace.getId(), exception, Duration.between(startedAt, Instant.now()));
                last = exception;
            }
        }
        throw new AiUnavailableException(last == null ? "No AI model is configured" : last.getMessage(), last);
    }

    public <T> T completeJson(AiRequest request, Class<T> responseType) {
        RuntimeException last = null;
        for (int validationAttempt = 0; validationAttempt < 2; validationAttempt++) {
            AiRequest candidate = validationAttempt == 0 ? request : new AiRequest(
                    request.capability(), request.userId(), request.courseId(), request.promptVersion(),
                    request.systemPrompt(), request.userPrompt()
                            + "\n\nThe previous response was invalid. Return a valid structured response only.",
                    request.toolCalls(), request.memoryContext());
            String userPrompt = structuredPrompt(candidate.userPrompt(), responseType);
            for (AiModelEndpoint endpoint : router.candidates(candidate.capability())) {
                Instant startedAt = Instant.now();
                AiTrace trace = traces.begin(candidate, endpoint.provider(), endpoint.model());
                try {
                    StructuredJsonAssistant assistant = serviceFactory.create(
                            StructuredJsonAssistant.class, endpoint, null);
                    Result<StructuredJsonEnvelope> result = assistant.chat(
                            normalizeSystemPrompt(candidate.systemPrompt()), userPrompt);
                    if (result == null || result.content() == null || result.content().json() == null) {
                        throw new IllegalArgumentException("AI returned an empty structured response");
                    }
                    T parsed = objectMapper.readValue(stripCodeFence(result.content().json()), responseType);
                    TokenUsage usage = result.tokenUsage();
                    traces.succeed(trace.getId(), usage == null ? null : usage.inputTokenCount(),
                            usage == null ? null : usage.outputTokenCount(),
                            Duration.between(startedAt, Instant.now()));
                    return parsed;
                } catch (JsonProcessingException | RuntimeException exception) {
                    RuntimeException failure = exception instanceof RuntimeException runtime
                            ? runtime : new IllegalArgumentException(exception);
                    traces.fail(trace.getId(), failure, Duration.between(startedAt, Instant.now()));
                    last = failure;
                }
            }
        }
        throw new AiUnavailableException("AI returned invalid structured output", last);
    }

    public AiStreamHandle stream(AiRequest request, Consumer<String> onDelta,
                                 Consumer<AiResponse> onComplete, Consumer<Throwable> onError) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AiStreamHandle handle = new AiStreamHandle(cancelled, new AtomicReference<>());
        streamCandidate(request, router.candidates(request.capability()), 0, new AtomicInteger(),
                new AtomicBoolean(), handle, onDelta, onComplete, onError);
        return handle;
    }

    private void streamCandidate(AiRequest request, List<AiModelEndpoint> candidates, int index,
                                 AtomicInteger emittedTokens, AtomicBoolean terminal, AiStreamHandle handle,
                                 Consumer<String> onDelta, Consumer<AiResponse> onComplete,
                                 Consumer<Throwable> onError) {
        if (handle.isCancelled() || terminal.get()) return;
        if (index >= candidates.size()) {
            if (terminal.compareAndSet(false, true)) {
                onError.accept(new AiUnavailableException("All configured AI models failed"));
            }
            return;
        }
        AiModelEndpoint endpoint = candidates.get(index);
        Instant startedAt = Instant.now();
        AiTrace trace = traces.begin(request, endpoint.provider(), endpoint.model());
        AtomicBoolean traceTerminal = new AtomicBoolean();
        AtomicReference<StreamingHandle> providerHandle = new AtomicReference<>();
        java.util.function.Predicate<Throwable> failTrace = error -> {
            if (traceTerminal.compareAndSet(false, true)) {
                traces.fail(trace.getId(), error, Duration.between(startedAt, Instant.now()));
                return true;
            }
            return false;
        };
        handle.onCancel(() -> {
            failTrace.test(new CancellationException("AI stream cancelled"));
            StreamingHandle current = providerHandle.get();
            if (current != null && !current.isCancelled()) current.cancel();
        });
        if (handle.isCancelled()) return;
        try {
            TokenStream tokenStream = tokenStream(request, endpoint);
            if (handle.isCancelled()) return;
            tokenStream.onPartialResponseWithContext((partialResponse, context) -> {
                bindProviderHandle(providerHandle, context, handle);
                if (!handle.isCancelled() && !terminal.get()
                        && partialResponse.text() != null && !partialResponse.text().isEmpty()) {
                    emittedTokens.incrementAndGet();
                    onDelta.accept(partialResponse.text());
                }
            }).onCompleteResponse(response -> {
                if (handle.isCancelled()) {
                    failTrace.test(new CancellationException("AI stream cancelled"));
                    return;
                }
                if (!traceTerminal.compareAndSet(false, true)) return;
                AiResponse result = response(response, endpoint, trace.getId());
                traces.succeed(trace.getId(), result.inputTokens(), result.outputTokens(),
                        Duration.between(startedAt, Instant.now()));
                if (terminal.compareAndSet(false, true)) {
                    onComplete.accept(result);
                }
            }).onError(error -> {
                Throwable failure = error == null ? new IllegalStateException("AI stream failed") : error;
                if (!failTrace.test(failure)) return;
                if (!handle.isCancelled() && emittedTokens.get() == 0 && index + 1 < candidates.size()) {
                    streamCandidate(request, candidates, index + 1, emittedTokens, terminal, handle,
                            onDelta, onComplete, onError);
                } else if (!handle.isCancelled() && terminal.compareAndSet(false, true)) {
                    onError.accept(failure);
                }
            }).start();
        } catch (RuntimeException error) {
            if (!failTrace.test(error)) return;
            if (!handle.isCancelled() && emittedTokens.get() == 0 && index + 1 < candidates.size()) {
                streamCandidate(request, candidates, index + 1, emittedTokens, terminal, handle,
                        onDelta, onComplete, onError);
            } else if (!handle.isCancelled() && terminal.compareAndSet(false, true)) {
                onError.accept(error);
            }
        }
    }

    private TokenStream tokenStream(AiRequest request, AiModelEndpoint endpoint) {
        if (request.memoryContext() == null) {
            StreamingAssistant assistant = serviceFactory.create(
                    StreamingAssistant.class, endpoint, null);
            return assistant.chat(normalizeSystemPrompt(request.systemPrompt()), request.userPrompt());
        }
        AiMemoryContext context = request.memoryContext();
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id(context.memoryId()).maxMessages(6).alwaysKeepSystemMessageFirst(true).build();
        if (!context.persistedHistory().isBlank()) {
            memory.add(UserMessage.from("Persisted conversation context:\n" + context.persistedHistory()));
        }
        ChatMemoryProvider provider = memoryId -> {
            if (!context.memoryId().equals(memoryId)) {
                throw new IllegalArgumentException("Unexpected AI memory identifier");
            }
            return memory;
        };
        MemoryStreamingAssistant assistant = serviceFactory.create(
                MemoryStreamingAssistant.class, endpoint, provider);
        return assistant.chat(context.memoryId(), normalizeSystemPrompt(request.systemPrompt()),
                request.userPrompt());
    }

    private static void bindProviderHandle(AtomicReference<StreamingHandle> target,
                                           PartialResponseContext context,
                                           AiStreamHandle applicationHandle) {
        if (context == null || context.streamingHandle() == null) return;
        target.compareAndSet(null, context.streamingHandle());
        if (applicationHandle.isCancelled() && !context.streamingHandle().isCancelled()) {
            context.streamingHandle().cancel();
        }
    }

    private String structuredPrompt(String userPrompt, Class<?> responseType) {
        return userPrompt
                + "\n\nPlace the target JSON document in the `json` string field. "
                + "The decoded `json` value must obey these target-format instructions:"
                + outputParser.outputFormatInstructions(responseType);
    }

    private static List<ChatMessage> messages(AiRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(SystemMessage.from(request.systemPrompt()));
        }
        messages.add(UserMessage.from(request.userPrompt()));
        return messages;
    }

    private static String normalizeSystemPrompt(String systemPrompt) {
        return systemPrompt == null || systemPrompt.isBlank()
                ? "Follow the user's instructions safely." : systemPrompt;
    }

    private static Map<AiToolCallRecorder, Integer> recorderOffsets(Object[] authorizedTools) {
        Map<AiToolCallRecorder, Integer> offsets = new IdentityHashMap<>();
        for (Object tool : authorizedTools) {
            if (tool instanceof AiToolCallRecorder recorder) {
                offsets.put(recorder, recorder.recordedToolCalls().size());
            }
        }
        return offsets;
    }

    private static void beginToolAttempt(Object[] authorizedTools) {
        for (Object tool : authorizedTools) {
            if (tool instanceof AiToolCallRecorder recorder) recorder.beginAiAttempt();
        }
    }

    private static List<AiToolCall> recordedSince(Map<AiToolCallRecorder, Integer> offsets) {
        List<AiToolCall> calls = new ArrayList<>();
        offsets.forEach((recorder, offset) -> {
            List<AiToolCall> recorded = recorder.recordedToolCalls();
            if (offset < recorded.size()) calls.addAll(recorded.subList(offset, recorded.size()));
        });
        return calls;
    }

    private static List<AiToolCall> mergeToolCalls(List<AiToolCall> initial,
                                                   List<ToolExecution> executions,
                                                   List<AiToolCall> recorded) {
        List<AiToolCall> calls = new ArrayList<>(initial);
        List<AiToolCall> unmatched = new ArrayList<>(recorded);
        for (ToolExecution execution : executions) {
            String name = execution.request().name();
            int match = -1;
            for (int index = 0; index < unmatched.size(); index++) {
                if (unmatched.get(index).name().equals(name)) {
                    match = index;
                    break;
                }
            }
            if (match >= 0) {
                AiToolCall call = unmatched.remove(match);
                calls.add(execution.hasFailed() && call.status() != AiToolCall.Status.FAILED
                        ? new AiToolCall(name, AiToolCall.Status.FAILED, call.latencyMs(),
                                "TOOL_EXECUTION_FAILED")
                        : call);
            } else {
                calls.add(new AiToolCall(name,
                        execution.hasFailed() ? AiToolCall.Status.FAILED : AiToolCall.Status.SUCCEEDED,
                        0, execution.hasFailed() ? "TOOL_EXECUTION_FAILED" : null));
            }
        }
        calls.addAll(unmatched);
        return List.copyOf(calls);
    }

    private static void requireSuccessfulTools(List<ToolExecution> executions, Set<String> required) {
        if (required.isEmpty()) return;
        Set<String> succeeded = new HashSet<>();
        executions.stream().filter(execution -> !execution.hasFailed())
                .map(execution -> execution.request().name()).forEach(succeeded::add);
        if (!succeeded.containsAll(required)) {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(succeeded);
            throw new IllegalStateException("AI did not execute required authorized tools: " + missing);
        }
    }

    private static AiResponse response(ChatResponse response, AiModelEndpoint endpoint, Long traceId) {
        TokenUsage usage = response.tokenUsage();
        return new AiResponse(response.aiMessage().text(), endpoint.provider(), endpoint.model(),
                usage == null ? null : usage.inputTokenCount(),
                usage == null ? null : usage.outputTokenCount(), traceId);
    }

    private static String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstNewline = trimmed.indexOf('\n');
        int closing = trimmed.lastIndexOf("```");
        return firstNewline >= 0 && closing > firstNewline
                ? trimmed.substring(firstNewline + 1, closing).trim() : trimmed;
    }

    interface ToolAwareAssistant {
        @dev.langchain4j.service.SystemMessage("{{instructions}}")
        Result<String> chat(@V("instructions") String instructions,
                            @dev.langchain4j.service.UserMessage String userMessage);
    }

    interface StreamingAssistant {
        @dev.langchain4j.service.SystemMessage("{{instructions}}")
        TokenStream chat(@V("instructions") String instructions,
                         @dev.langchain4j.service.UserMessage String userMessage);
    }

    interface MemoryStreamingAssistant {
        @dev.langchain4j.service.SystemMessage("{{instructions}}")
        TokenStream chat(@MemoryId String memoryId,
                         @V("instructions") String instructions,
                         @dev.langchain4j.service.UserMessage String userMessage);
    }

    interface StructuredJsonAssistant {
        @dev.langchain4j.service.SystemMessage("{{instructions}}")
        Result<StructuredJsonEnvelope> chat(@V("instructions") String instructions,
                                            @dev.langchain4j.service.UserMessage String userMessage);
    }

    public record StructuredJsonEnvelope(
            @Description("A JSON document encoded as a string; do not include Markdown fences") String json) {
    }
}
