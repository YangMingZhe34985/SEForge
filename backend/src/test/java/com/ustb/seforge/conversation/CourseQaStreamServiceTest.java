package com.ustb.seforge.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ustb.seforge.ai.application.AiGateway;
import com.ustb.seforge.ai.application.AiRequest;
import com.ustb.seforge.ai.application.AiResponse;
import com.ustb.seforge.ai.application.AiStreamHandle;
import com.ustb.seforge.ai.application.PromptCatalog;
import com.ustb.seforge.ai.application.PromptCatalog.PromptTemplate;
import com.ustb.seforge.content.service.CourseKnowledgeSearchService;
import com.ustb.seforge.content.service.KnowledgeEvidence;
import com.ustb.seforge.conversation.api.AskQuestionRequest;
import com.ustb.seforge.conversation.api.ConversationView;
import com.ustb.seforge.conversation.api.MessageView;
import com.ustb.seforge.conversation.domain.ConversationStatus;
import com.ustb.seforge.conversation.domain.MessageRole;
import com.ustb.seforge.conversation.domain.MessageStatus;
import com.ustb.seforge.conversation.service.ConversationService;
import com.ustb.seforge.conversation.service.CourseQaStreamService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class CourseQaStreamServiceTest {
    @Mock ConversationService conversations;
    @Mock CourseKnowledgeSearchService knowledge;
    @Mock PromptCatalog prompts;
    @Mock AiGateway ai;

    @Test
    void providerCannotEmitASecondTerminalEvent() {
        TaskExecutor sameThread = Runnable::run;
        CourseQaStreamService service = new CourseQaStreamService(
                conversations, knowledge, prompts, ai, sameThread);
        AskQuestionRequest request = new AskQuestionRequest("req-1", "What is cohesion?");
        ConversationView conversation = new ConversationView(21L, 11L, "Question",
                ConversationStatus.ACTIVE, null, Instant.now(), Instant.now());
        when(conversations.addQuestion(11L, 21L, 7L, "req-1", request.content()))
                .thenReturn(new ConversationService.QuestionContext(conversation, 31L,
                        "USER: Earlier question\nASSISTANT: Earlier answer", false));
        KnowledgeEvidence evidence = new KnowledgeEvidence("v1", 41L, 51L, null,
                "notes.pdf", 3, "Design", "High cohesion keeps responsibilities focused.", 0.91);
        when(knowledge.search(11L, request.content(), 5)).thenReturn(List.of(evidence));
        when(prompts.load("course-qa", "v1")).thenReturn(new PromptTemplate("course-qa", "v1", "system"));
        MessageView saved = new MessageView(61L, MessageRole.ASSISTANT, "Focused responsibilities.",
                MessageStatus.COMPLETE, List.of(), Instant.now());
        when(conversations.addAssistant(eq(11L), eq(21L), eq(7L), any(), any(), any()))
                .thenReturn(saved);
        when(ai.stream(any(), any(), any(), any())).thenAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(1);
            Consumer<AiResponse> onComplete = invocation.getArgument(2);
            Consumer<Throwable> onError = invocation.getArgument(3);
            onDelta.accept("Focused responsibilities.");
            onComplete.accept(new AiResponse("Focused responsibilities.", "fake", "fake", 3, 2));
            onError.accept(new IllegalStateException("late provider callback"));
            return mock(AiStreamHandle.class);
        });

        SseEmitter emitter = service.open(11L, 21L, 7L, request);
        List<ObservedEvent> events = events(emitter);

        assertThat(events).extracting(ObservedEvent::type)
                .containsSubsequence("citation", "message.delta", "done")
                .doesNotContain("error");
        assertThat(events.stream().filter(event -> event.type().equals("done")
                || event.type().equals("error"))).hasSize(1);
        assertThat(events).extracting(ObservedEvent::requestId).containsOnly("req-1");
        ArgumentCaptor<AiRequest> aiRequest = ArgumentCaptor.forClass(AiRequest.class);
        org.mockito.Mockito.verify(ai).stream(aiRequest.capture(), any(), any(), any());
        assertThat(aiRequest.getValue().memoryContext()).isNotNull();
        assertThat(aiRequest.getValue().memoryContext().memoryId()).isEqualTo("11:21:req-1");
        assertThat(aiRequest.getValue().memoryContext().persistedHistory()).contains("Earlier answer");
        assertThat(aiRequest.getValue().userPrompt()).doesNotContain("Conversation history:");
    }

    private List<ObservedEvent> events(SseEmitter emitter) {
        Set<?> pending = (Set<?>) ReflectionTestUtils.getField(emitter, "earlySendAttempts");
        List<ObservedEvent> events = new ArrayList<>();
        if (pending == null) return events;
        for (Object item : pending) {
            Object data = ReflectionTestUtils.getField(item, "data");
            if (data != null && data.getClass().getSimpleName().equals("StreamEvent")) {
                events.add(new ObservedEvent(
                        ReflectionTestUtils.invokeMethod(data, "type"),
                        ReflectionTestUtils.invokeMethod(data, "requestId")));
            }
        }
        return events;
    }

    private record ObservedEvent(String type, String requestId) {
    }
}
