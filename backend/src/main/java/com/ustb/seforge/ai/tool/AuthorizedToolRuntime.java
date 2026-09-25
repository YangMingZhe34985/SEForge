package com.ustb.seforge.ai.tool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.application.AiToolCall;
import com.ustb.seforge.ai.application.AiToolCallRecorder;
import com.ustb.seforge.common.exception.AppException;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class AuthorizedToolRuntime {
    private final ObjectMapper mapper;
    private final ExecutorService executor = new ThreadPoolExecutor(4, 16, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "seforge-authorized-tool");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public AuthorizedToolRuntime(ObjectMapper mapper) { this.mapper = mapper; }

    public Session open(ToolContext context, List<ToolDefinition<?, ?>> tools, int maxCalls, Duration timeout) {
        if (maxCalls < 1 || maxCalls > 32 || timeout == null || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) throw new IllegalArgumentException("Invalid tool budget");
        return new Session(context, tools, maxCalls, timeout);
    }

    @PreDestroy
    public void close() { executor.shutdownNow(); }

    public final class Session implements AiToolCallRecorder {
        private final ToolContext context;
        private final Map<String, ToolDefinition<?, ?>> definitions;
        private final List<AiToolCall> calls = new CopyOnWriteArrayList<>();
        private final AtomicInteger used = new AtomicInteger();
        private final int maxCalls;
        private final Duration timeout;

        private Session(ToolContext context, List<ToolDefinition<?, ?>> tools, int maxCalls, Duration timeout) {
            this.context = Objects.requireNonNull(context);
            Map<String, ToolDefinition<?, ?>> allowed = new LinkedHashMap<>();
            for (var definition : tools) {
                String name = definition.specification().name();
                if (!context.allowedTools().contains(name) || allowed.putIfAbsent(name, definition) != null) {
                    throw new ToolFailureException(ToolErrorCode.FORBIDDEN);
                }
            }
            this.definitions = Map.copyOf(allowed);
            this.maxCalls = maxCalls;
            this.timeout = timeout;
        }

        public Map<ToolSpecification, ToolExecutor> executors() {
            Map<ToolSpecification, ToolExecutor> result = new LinkedHashMap<>();
            definitions.values().forEach(tool -> result.put(tool.specification(),
                    (request, ignoredMemoryId) -> invoke(request.name(), request.arguments())));
            return Map.copyOf(result);
        }

        public void requireScope(Long userId, Long courseId) {
            if (!context.userId().equals(userId) || !context.courseId().equals(courseId)) {
                throw new ToolFailureException(ToolErrorCode.FORBIDDEN);
            }
        }

        public String invoke(String name, String arguments) {
            long started = System.nanoTime();
            ToolDefinition<?, ?> definition = definitions.get(name);
            ToolErrorCode failure = null;
            int count = 0;
            Future<Object> task = null;
            try {
                if (definition == null) throw new ToolFailureException(ToolErrorCode.FORBIDDEN);
                if (used.incrementAndGet() > maxCalls) throw new ToolFailureException(ToolErrorCode.BUDGET_EXCEEDED);
                if (arguments == null || arguments.length() > 8000) throw new ToolFailureException(ToolErrorCode.INVALID_ARGUMENT);
                JsonNode input = mapper.readTree(arguments);
                if (input == null || !input.isObject() || containsScope(input)) throw new ToolFailureException(ToolErrorCode.INVALID_ARGUMENT);
                task = executor.submit(() -> execute(definition, input));
                Object result = task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                count = result instanceof Collection<?> values ? values.size() : result == null ? 0 : 1;
                String json = mapper.writeValueAsString(result);
                if (count > 100 || json.length() > 32000) throw new ToolFailureException(ToolErrorCode.BUDGET_EXCEEDED);
                return json;
            } catch (Exception exception) {
                failure = classify(exception);
                if (task != null) task.cancel(true);
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new ToolFailureException(failure);
            } finally {
                // No model arguments or result bodies enter telemetry.
                calls.add(new AiToolCall(definition == null ? "unregistered_tool" : name,
                        failure == null ? AiToolCall.Status.SUCCEEDED : AiToolCall.Status.FAILED,
                        Math.max(0, (System.nanoTime() - started) / 1_000_000),
                        failure == null ? null : failure.name(), definition == null ? "unknown" : definition.version(),
                        failure == null ? count : 0, failure != null && failure.retryable()));
            }
        }

        private <I, O> Object execute(ToolDefinition<I, O> tool, JsonNode input) throws Exception {
            // Registration is not an authorization cache: execute on every call/retry.
            tool.authorize().accept(context);
            I typed = mapper.readerFor(tool.inputType())
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .readValue(input);
            return tool.read().apply(context, typed);
        }

        @Override public List<AiToolCall> recordedToolCalls() { return List.copyOf(calls); }
        // Do not reset the request budget on provider fallback or a repair attempt.
    }

    private static boolean containsScope(JsonNode node) {
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String key = field.getKey().replace("_", "").toLowerCase(Locale.ROOT);
                if (Set.of("userid", "courseid", "assignmentid", "questionid", "submissionid", "role").contains(key)
                        || containsScope(field.getValue())) return true;
            }
        } else if (node.isArray()) for (var value : node) if (containsScope(value)) return true;
        return false;
    }

    private static ToolErrorCode classify(Throwable failure) {
        if (failure instanceof ExecutionException && failure.getCause() != null) return classify(failure.getCause());
        if (failure instanceof ToolFailureException tool) return tool.code();
        if (failure instanceof TimeoutException || failure instanceof InterruptedException) return ToolErrorCode.TIMEOUT;
        if (failure instanceof RejectedExecutionException) return ToolErrorCode.BUDGET_EXCEEDED;
        if (failure instanceof SecurityException || failure instanceof org.springframework.security.access.AccessDeniedException) return ToolErrorCode.FORBIDDEN;
        if (failure instanceof AppException app) return switch (app.getErrorCode().status().value()) {
            case 400 -> ToolErrorCode.INVALID_ARGUMENT;
            case 401, 403 -> ToolErrorCode.FORBIDDEN;
            case 404, 410 -> ToolErrorCode.NOT_FOUND;
            case 409 -> ToolErrorCode.FORBIDDEN;
            case 429 -> ToolErrorCode.BUDGET_EXCEEDED;
            default -> ToolErrorCode.DEPENDENCY_FAILURE;
        };
        if (failure instanceof IllegalArgumentException || failure instanceof com.fasterxml.jackson.core.JsonProcessingException) return ToolErrorCode.INVALID_ARGUMENT;
        return ToolErrorCode.DEPENDENCY_FAILURE;
    }
}
