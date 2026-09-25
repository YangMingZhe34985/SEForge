package com.ustb.seforge.ai;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.tool.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AuthorizedToolRuntimeTest {
    private final ToolContext scope = new ToolContext(7L, 9L, 11L, 12L, 13L, Set.of("lookup"), "request", "trace");
    private ToolSpecification spec() { return ToolSpecification.builder().name("lookup").description("Read authorized data")
            .parameters(JsonObjectSchema.builder().addStringProperty("query").required("query").build()).build(); }
    public record Input(String query) {}
    public record Output(String value) {}

    @Test void bindsServerScopeAndReauthorizesEveryExecution() throws Exception {
        var runtime = new AuthorizedToolRuntime(new ObjectMapper());
        try {
            AtomicBoolean permitted = new AtomicBoolean(true);
            AtomicInteger checks = new AtomicInteger();
            var definition = new ToolDefinition<>(spec(), "v1", Input.class, ctx -> {
                checks.incrementAndGet();
                if (!permitted.get()) throw new SecurityException("private denial detail");
            }, (ctx, input) -> new Output(ctx.userId() + ":" + ctx.courseId()));
            var session = runtime.open(scope, List.of(definition), 8, Duration.ofSeconds(1));
            assertThat(session.invoke("lookup", "{\"query\":\"secret prompt\"}")).contains("7:9");
            permitted.set(false);
            assertThatThrownBy(() -> session.invoke("lookup", "{\"query\":\"x\"}"))
                    .isInstanceOf(ToolFailureException.class).hasMessage("FORBIDDEN");
            assertThat(checks).hasValue(2);
            String trace = new ObjectMapper().writeValueAsString(session.recordedToolCalls());
            assertThat(trace).contains("toolVersion", "resultCount", "FORBIDDEN").doesNotContain("secret prompt", "private denial", "7:9");
        } finally { runtime.close(); }
    }

    @Test void rejectsScopeForgeryUnknownToolsAndBudgetResetOnFallback() {
        var runtime = new AuthorizedToolRuntime(new ObjectMapper());
        try {
            var definition = new ToolDefinition<>(spec(), "v1", Input.class, ctx -> {}, (ctx, input) -> new Output("safe"));
            var session = runtime.open(scope, List.of(definition), 8, Duration.ofSeconds(1));
            for (String key : List.of("userId", "courseId", "assignmentId", "questionId", "submissionId", "role")) {
                assertThatThrownBy(() -> session.invoke("lookup", "{\"query\":\"x\",\"" + key + "\":99}"))
                        .hasMessage("INVALID_ARGUMENT");
            }
            assertThatThrownBy(() -> session.invoke("unregistered", "{}" )).hasMessage("FORBIDDEN");
            session.invoke("lookup", "{\"query\":\"x\"}");
            session.beginAiAttempt();
            session.invoke("lookup", "{\"query\":\"x\"}");
            assertThatThrownBy(() -> session.invoke("lookup", "{\"query\":\"x\"}")).hasMessage("BUDGET_EXCEEDED");
        } finally { runtime.close(); }
    }

    @Test void timeoutAndDependencyFailureAreRetryableButDenialsAreNot() {
        var runtime = new AuthorizedToolRuntime(new ObjectMapper());
        try {
            var slow = new ToolDefinition<>(spec(), "v1", Input.class, ctx -> {}, (ctx, input) -> {
                try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return new Output("late");
            });
            var session = runtime.open(scope, List.of(slow), 1, Duration.ofMillis(20));
            assertThatThrownBy(() -> session.invoke("lookup", "{\"query\":\"x\"}"))
                    .isInstanceOfSatisfying(ToolFailureException.class, e -> {
                        assertThat(e.code()).isEqualTo(ToolErrorCode.TIMEOUT);
                        assertThat(e.retryable()).isTrue();
                    });
            assertThat(ToolErrorCode.FORBIDDEN.retryable()).isFalse();
            assertThat(ToolErrorCode.NOT_FOUND.retryable()).isFalse();
            assertThat(ToolErrorCode.DEPENDENCY_FAILURE.retryable()).isTrue();
        } finally { runtime.close(); }
    }
}
