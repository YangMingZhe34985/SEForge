package com.ustb.seforge.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.application.AiGateway;
import com.ustb.seforge.ai.application.AiMemoryContext;
import com.ustb.seforge.ai.application.AiRequest;
import com.ustb.seforge.ai.application.AiResponse;
import com.ustb.seforge.ai.application.AiTraceService;
import com.ustb.seforge.ai.application.AiToolCall;
import com.ustb.seforge.ai.application.AiToolCallRecorder;
import com.ustb.seforge.ai.application.AiUnavailableException;
import com.ustb.seforge.ai.application.ModelCapability;
import com.ustb.seforge.ai.domain.AiTrace;
import com.ustb.seforge.ai.infrastructure.AiModelEndpoint;
import com.ustb.seforge.ai.infrastructure.AiServiceFactory;
import com.ustb.seforge.ai.infrastructure.ModelRegistry;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import com.ustb.seforge.ai.infrastructure.ModelRouter;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiGatewayTest {
    private final ModelRouter router = mock(ModelRouter.class);
    private final AiTraceService traces = mock(AiTraceService.class);
    private final AiTrace trace = mock(AiTrace.class);
    private final AiRequest request = new AiRequest(ModelCapability.REASONING, 7L, 9L,
            "test-v1", "System", "Question");
    private AiGateway gateway;

    @BeforeEach
    void setUp() {
        when(trace.getId()).thenReturn(1L);
        when(traces.begin(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(trace);
        gateway = new AiGateway(router, traces, new ObjectMapper(),
                new AiServiceFactory(mock(ModelRegistry.class)));
    }

    @Test
    void fallsBackWhenPrimaryProviderTimesOutOrRateLimits() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        when(primary.chat(anyList())).thenThrow(new RuntimeException("HTTP 429 or timeout"));
        when(fallback.chat(anyList())).thenReturn(response("fallback answer"));
        when(router.candidates(ModelCapability.REASONING)).thenReturn(List.of(
                endpoint("deepseek", primary), endpoint("dashscope", fallback)));

        var result = gateway.complete(request);

        assertThat(result.text()).isEqualTo("fallback answer");
        assertThat(result.provider()).isEqualTo("dashscope");
        assertThat(result.traceId()).isEqualTo(1L);
        verify(traces).fail(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(RuntimeException.class),
                org.mockito.ArgumentMatchers.any());
        verify(traces).succeed(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidStructuredOutputAfterOneRepairAttempt() {
        ScriptedChatModel model = new ScriptedChatModel("not-json", "still-not-json");
        when(router.candidates(ModelCapability.REASONING))
                .thenReturn(List.of(endpoint("fake", model)));

        assertThatThrownBy(() -> gateway.completeJson(request, StructuredAnswer.class))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("invalid structured output");
        assertThat(model.invocations()).isEqualTo(2);
    }

    @Test
    void structuredAiServiceFallsBackAndValidatesDecodedTarget() {
        ScriptedChatModel primary = new ScriptedChatModel("not-json");
        ScriptedChatModel fallback = new ScriptedChatModel(
                "{\"json\":\"{\\\"answer\\\":\\\"validated fallback\\\"}\"}");
        when(router.candidates(ModelCapability.REASONING)).thenReturn(List.of(
                endpoint("primary", primary), endpoint("fallback", fallback)));

        StructuredAnswer result = gateway.completeJson(request, StructuredAnswer.class);

        assertThat(result.answer()).isEqualTo("validated fallback");
        assertThat(primary.invocations()).isEqualTo(1);
        assertThat(fallback.invocations()).isEqualTo(1);
        assertThat(fallback.lastRequest().messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(UserMessage::singleText))
                .anyMatch(message -> message.contains("answer") && message.contains("`json`"));
        verify(traces).fail(eq(1L), any(RuntimeException.class), any());
        verify(traces).succeed(eq(1L), eq(3), eq(5), any());
    }

    @Test
    void structuredAiServiceRepairsOnceAfterAllCandidatesReturnInvalidData() {
        ScriptedChatModel model = new ScriptedChatModel(
                "{\"json\":\"not-target-json\"}",
                "{\"json\":\"{\\\"answer\\\":\\\"repaired\\\"}\"}");
        when(router.candidates(ModelCapability.REASONING))
                .thenReturn(List.of(endpoint("fake", model)));

        StructuredAnswer result = gateway.completeJson(request, StructuredAnswer.class);

        assertThat(result.answer()).isEqualTo("repaired");
        assertThat(model.invocations()).isEqualTo(2);
        assertThat(model.lastRequest().messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(UserMessage::singleText))
                .anyMatch(message -> message.contains("previous response was invalid"));
    }

    @Test
    void streamingFallsBackWhenPrimaryThrowsBeforeRegisteringCallbacks() {
        StreamingChatModel primary = mock(StreamingChatModel.class);
        StreamingChatModel fallback = mock(StreamingChatModel.class);
        doThrow(new RuntimeException("provider unavailable"))
                .when(primary).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onCompleteResponse(response("fallback stream"));
            return null;
        }).when(fallback).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        when(router.candidates(ModelCapability.REASONING)).thenReturn(List.of(
                endpoint("primary", mock(ChatModel.class), primary),
                endpoint("fallback", mock(ChatModel.class), fallback)));
        AtomicReference<String> completed = new AtomicReference<>();
        AtomicReference<Throwable> failed = new AtomicReference<>();

        gateway.stream(request, ignored -> {}, response -> completed.set(response.text()), failed::set);

        assertThat(completed.get()).isEqualTo("fallback stream");
        assertThat(failed.get()).isNull();
        verify(traces).fail(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(RuntimeException.class),
                org.mockito.ArgumentMatchers.any());
        verify(traces).succeed(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancellationClosesTraceEvenWhenProviderNeverCallsBack() {
        StreamingChatModel streaming = mock(StreamingChatModel.class);
        when(router.candidates(ModelCapability.REASONING)).thenReturn(List.of(
                endpoint("slow", mock(ChatModel.class), streaming)));

        var handle = gateway.stream(request, ignored -> {}, ignored -> {}, ignored -> {});
        handle.cancel();

        verify(traces).fail(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.isA(CancellationException.class),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancellationReachesTokenStreamProviderHandleAndKeepsTraceTerminal() {
        TestStreamingHandle providerHandle = new TestStreamingHandle();
        StreamingChatModel streaming = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                handler.onPartialResponse(new PartialResponse("first"),
                        new PartialResponseContext(providerHandle));
            }
        };
        when(router.candidates(ModelCapability.REASONING)).thenReturn(List.of(
                endpoint("slow", mock(ChatModel.class), streaming)));

        var handle = gateway.stream(request, ignored -> {}, ignored -> {}, ignored -> {});
        handle.cancel();

        assertThat(providerHandle.isCancelled()).isTrue();
        verify(traces).fail(eq(1L), org.mockito.ArgumentMatchers.isA(CancellationException.class), any());
    }

    @Test
    void requestScopedMemoryProviderSeedsPersistedHistoryWithoutDuplicatingCurrentPrompt() {
        CapturingStreamingModel streaming = new CapturingStreamingModel();
        when(router.candidates(ModelCapability.REASONING)).thenReturn(List.of(
                endpoint("fake", mock(ChatModel.class), streaming)));
        AiRequest memoryRequest = new AiRequest(ModelCapability.REASONING, 7L, 9L,
                "course-qa:v1", "System", "Current evidence and question",
                List.of(), new AiMemoryContext("9:21:req-1",
                        "USER: Earlier question\nASSISTANT: Earlier answer"));
        AtomicReference<AiResponse> completed = new AtomicReference<>();

        gateway.stream(memoryRequest, ignored -> {}, completed::set, ignored -> {});

        assertThat(completed.get()).isNotNull();
        List<String> userMessages = streaming.request().messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(UserMessage::singleText)
                .toList();
        assertThat(userMessages).anyMatch(message -> message.contains("Earlier question")
                && message.contains("Earlier answer"));
        assertThat(userMessages).filteredOn(message -> message.contains("Current evidence and question"))
                .hasSize(1);
    }

    @Test
    void ignoresProviderCallbacksAfterTheFirstTerminalSignal() {
        StreamingChatModel streaming = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                handler.onCompleteResponse(response("complete"));
                handler.onError(new IllegalStateException("late failure"));
            }
        };
        when(router.candidates(ModelCapability.REASONING)).thenReturn(List.of(
                endpoint("fake", mock(ChatModel.class), streaming)));
        AtomicInteger completions = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        gateway.stream(request, ignored -> {}, ignored -> completions.incrementAndGet(),
                ignored -> errors.incrementAndGet());

        assertThat(completions).hasValue(1);
        assertThat(errors).hasValue(0);
    }

    @Test
    void aiServiceExecutesBoundToolTracksItAndFallsBackWhenPrimarySkipsRequiredTool() {
        ChatModel primary = new DirectAnswerModel();
        ChatModel fallback = new ToolCallingModel();
        when(router.candidates(ModelCapability.REASONING)).thenReturn(List.of(
                endpoint("primary", primary), endpoint("fallback", fallback)));
        AiServiceFactory factory = new AiServiceFactory(mock(ModelRegistry.class));
        gateway = new AiGateway(router, traces, new ObjectMapper(), factory);
        BoundTool tool = new BoundTool("course-9-user-7");

        var result = gateway.completeWithTools(request, Set.of("read_bound_context"), tool);

        assertThat(result.response().text()).isEqualTo("authorized answer");
        assertThat(result.response().provider()).isEqualTo("fallback");
        assertThat(result.toolExecutions()).singleElement().satisfies(execution -> {
            assertThat(execution.name()).isEqualTo("read_bound_context");
            assertThat(execution.result()).isEqualTo("course-9-user-7");
            assertThat(execution.failed()).isFalse();
        });
        verify(traces).fail(eq(1L), any(IllegalStateException.class), any(), anyList());
        verify(traces).succeed(eq(1L), any(), any(), any(),
                argThat(calls -> calls.size() == 1
                        && calls.get(0).name().equals("read_bound_context")
                        && calls.get(0).status() == AiToolCall.Status.SUCCEEDED));
    }

    private AiModelEndpoint endpoint(String provider, ChatModel model) {
        return new AiModelEndpoint(provider, provider + "-model", model, mock(StreamingChatModel.class));
    }

    private AiModelEndpoint endpoint(String provider, ChatModel model, StreamingChatModel streaming) {
        return new AiModelEndpoint(provider, provider + "-model", model, streaming);
    }

    private ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text))
                .tokenUsage(new TokenUsage(3, 5)).build();
    }

    private record StructuredAnswer(String answer) {
    }

    private final class ScriptedChatModel implements ChatModel {
        private final Queue<String> responses = new ArrayDeque<>();
        private final AtomicInteger invocations = new AtomicInteger();
        private volatile ChatRequest lastRequest;

        private ScriptedChatModel(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            lastRequest = chatRequest;
            invocations.incrementAndGet();
            String next = responses.poll();
            if (next == null) throw new IllegalStateException("No scripted response");
            return response(next);
        }

        private int invocations() { return invocations.get(); }

        private ChatRequest lastRequest() { return lastRequest; }
    }

    private final class CapturingStreamingModel implements StreamingChatModel {
        private volatile ChatRequest request;

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            request = chatRequest;
            handler.onCompleteResponse(response("answer"));
        }

        private ChatRequest request() { return request; }
    }

    private static final class TestStreamingHandle implements StreamingHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void cancel() { cancelled.set(true); }

        @Override
        public boolean isCancelled() { return cancelled.get(); }
    }

    private static final class BoundTool implements AiToolCallRecorder {
        private final String boundContext;
        private final List<AiToolCall> calls = new java.util.ArrayList<>();

        private BoundTool(String boundContext) {
            this.boundContext = boundContext;
        }

        @Tool(name = "read_bound_context", value = "Read the server-bound authorization context")
        public String readBoundContext() {
            calls.add(AiToolCall.succeeded("read_bound_context", 1));
            return boundContext;
        }

        @Override
        public List<AiToolCall> recordedToolCalls() {
            return List.copyOf(calls);
        }
    }

    private static final class DirectAnswerModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(AiMessage.from("answer without tools"))
                    .tokenUsage(new TokenUsage(1, 1)).build();
        }
    }

    private static final class ToolCallingModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            boolean hasToolResult = request.messages().stream()
                    .anyMatch(ToolExecutionResultMessage.class::isInstance);
            if (!hasToolResult) {
                assertThat(request.toolSpecifications()).extracting(specification -> specification.name())
                        .containsExactly("read_bound_context");
                ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                        .id("tool-1").name("read_bound_context").arguments("{}").build();
                return ChatResponse.builder().aiMessage(AiMessage.from(toolRequest))
                        .tokenUsage(new TokenUsage(2, 1)).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("authorized answer"))
                    .tokenUsage(new TokenUsage(3, 4)).build();
        }
    }
}
