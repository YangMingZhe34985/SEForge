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
    void retrievalFailureEmitsDiagnosticTerminalWithoutCallingAiOrSavingAnswer() {
        var service = new CourseQaStreamService(conversations, knowledge, prompts, ai, Runnable::run);
        var conversation = new ConversationView(21L,11L,"Question",ConversationStatus.ACTIVE,null,Instant.now(),Instant.now());
        when(conversations.addQuestion(11L,21L,7L,"infra-1","question"))
                .thenReturn(new ConversationService.QuestionContext(conversation,31L,"",false));
        when(knowledge.search(11L,"question",5)).thenThrow(new com.ustb.seforge.common.exception.AppException(
                com.ustb.seforge.common.exception.ErrorCode.VECTOR_STORE_UNAVAILABLE, "Milvus unavailable"));
        var emitter = service.open(11L,21L,7L,new AskQuestionRequest("infra-1","question"));
        assertThat(events(emitter)).extracting(ObservedEvent::type).containsExactly("error");
        Set<?> pending = (Set<?>) ReflectionTestUtils.getField(emitter, "earlySendAttempts");
        assertThat(pending.stream().map(item -> String.valueOf(ReflectionTestUtils.getField(item, "data")))
                .collect(java.util.stream.Collectors.joining())).contains("VECTOR_STORE_UNAVAILABLE", "Milvus unavailable");
        org.mockito.Mockito.verifyNoInteractions(ai);
        org.mockito.Mockito.verify(conversations,org.mockito.Mockito.never()).addAssistant(any(),any(),any(),any(),any(),any());
    }

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
        when(prompts.load("course-qa", "v2")).thenReturn(new PromptTemplate("course-qa", "v2", "system"));
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

    @Test
    void cancellationBeatsLateProviderCompletionAndNeverPersistsPrefix() {
        var service = new CourseQaStreamService(conversations, knowledge, prompts, ai, Runnable::run);
        var conversation = new ConversationView(21L,11L,"Question",ConversationStatus.ACTIVE,null,Instant.now(),Instant.now());
        when(conversations.addQuestion(11L,21L,7L,"cancel-1","cohesion"))
                .thenReturn(new ConversationService.QuestionContext(conversation,31L,"",false));
        when(knowledge.search(11L,"cohesion",5)).thenReturn(List.of(new KnowledgeEvidence("v",41L,51L,null,"a.txt",0,"Text","cohesion",1.0)));
        when(prompts.load("course-qa","v2")).thenReturn(new PromptTemplate("course-qa","v2","system"));
        var handle = mock(AiStreamHandle.class);
        when(ai.stream(any(),any(),any(),any())).thenAnswer(call -> {
            Consumer<String> delta = call.getArgument(1);
            Consumer<AiResponse> complete = call.getArgument(2);
            Consumer<Throwable> error = call.getArgument(3);
            delta.accept("unfinished prefix");
            service.cancel(7L,"cancel-1");
            complete.accept(new AiResponse("unfinished prefix","stub","stub",1,1));
            error.accept(new IllegalStateException("late error containing sensitive internals"));
            delta.accept("late delta");
            return handle;
        });
        var observed = events(service.open(11L,21L,7L,new AskQuestionRequest("cancel-1","cohesion")));
        assertThat(observed).extracting(ObservedEvent::type).containsExactly("citation","message.delta","error");
        org.mockito.Mockito.verify(conversations,org.mockito.Mockito.never()).addAssistant(any(),any(),any(),any(),any(),any());
        org.mockito.Mockito.verify(handle).cancel();
    }

    @Test
    void noEvidenceRefusesWithoutCallingChatModel() {
        var service = new CourseQaStreamService(conversations,knowledge,prompts,ai,Runnable::run);
        var conversation = new ConversationView(21L,11L,"Question",ConversationStatus.ACTIVE,null,Instant.now(),Instant.now());
        when(conversations.addQuestion(11L,21L,7L,"refuse-1","unknown"))
                .thenReturn(new ConversationService.QuestionContext(conversation,31L,"",false));
        when(knowledge.search(11L,"unknown",5)).thenReturn(List.of());
        when(conversations.addAssistant(eq(11L),eq(21L),eq(7L),any(),org.mockito.ArgumentMatchers.isNull(),eq(List.of())))
                .thenReturn(new MessageView(61L,MessageRole.ASSISTANT,"refusal",MessageStatus.COMPLETE,List.of(),Instant.now()));
        assertThat(events(service.open(11L,21L,7L,new AskQuestionRequest("refuse-1","unknown"))))
                .extracting(ObservedEvent::type).containsExactly("message.delta","done");
        org.mockito.Mockito.verifyNoInteractions(ai,prompts);
    }

    private record ObservedEvent(String type, String requestId) {
    }
}
