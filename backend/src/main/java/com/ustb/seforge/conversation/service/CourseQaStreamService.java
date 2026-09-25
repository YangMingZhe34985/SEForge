package com.ustb.seforge.conversation.service;

import com.ustb.seforge.ai.application.AiGateway;
import com.ustb.seforge.ai.application.AiMemoryContext;
import com.ustb.seforge.ai.application.AiRequest;
import com.ustb.seforge.ai.application.AiResponse;
import com.ustb.seforge.ai.application.AiStreamHandle;
import com.ustb.seforge.ai.application.AiToolCall;
import com.ustb.seforge.ai.application.ModelCapability;
import com.ustb.seforge.ai.application.PromptCatalog;
import com.ustb.seforge.ai.application.PromptCatalog.PromptTemplate;
import com.ustb.seforge.common.web.TraceContext;
import com.ustb.seforge.content.service.CourseKnowledgeSearchService;
import com.ustb.seforge.content.service.KnowledgeEvidence;
import com.ustb.seforge.conversation.api.AskQuestionRequest;
import com.ustb.seforge.conversation.api.CitationView;
import com.ustb.seforge.conversation.api.MessageView;
import com.ustb.seforge.conversation.service.ConversationService.QuestionContext;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class CourseQaStreamService {
    private static final String REFUSAL = "当前课程知识库中没有足够可靠的依据回答这个问题。请补充资料或换一种问法。";

    private final ConversationService conversations;
    private final CourseKnowledgeSearchService knowledge;
    private final PromptCatalog prompts;
    private final AiGateway ai;
    private final TaskExecutor executor;
    private final Map<SessionKey, StreamSession> active = new ConcurrentHashMap<>();
    private final AtomicLong eventSequence = new AtomicLong();

    public CourseQaStreamService(ConversationService conversations, CourseKnowledgeSearchService knowledge,
                                 PromptCatalog prompts, AiGateway ai,
                                 @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.conversations = conversations;
        this.knowledge = knowledge;
        this.prompts = prompts;
        this.ai = ai;
        this.executor = executor;
    }

    public SseEmitter open(Long courseId, Long conversationId, Long userId, AskQuestionRequest request) {
        SseEmitter emitter = new SseEmitter(10 * 60_000L);
        SessionKey key = new SessionKey(userId, request.requestId());
        StreamSession session = new StreamSession(key, emitter, request.requestId(), TraceContext.getOrCreate());
        StreamSession collision = active.putIfAbsent(key, session);
        if (collision != null) {
            session.error("DUPLICATE_REQUEST", "A stream with this requestId is already active");
            return emitter;
        }
        emitter.onCompletion(session::abort);
        emitter.onTimeout(() -> session.cancel("TIMEOUT", "Stream timed out"));
        emitter.onError(ignored -> session.abort());
        executor.execute(() -> run(courseId, conversationId, userId, request, session));
        return emitter;
    }

    public boolean cancel(Long userId, String requestId) {
        StreamSession session = active.get(new SessionKey(userId, requestId));
        if (session == null) return false;
        session.cancel("CANCELLED", "Request cancelled");
        return true;
    }

    @Scheduled(fixedDelay = 15_000)
    void heartbeat() {
        active.values().forEach(session -> session.event("heartbeat", Map.of("at", Instant.now().toString())));
    }

    private void run(Long courseId, Long conversationId, Long userId,
                     AskQuestionRequest request, StreamSession session) {
        try {
            if (session.isTerminal()) return;
            QuestionContext context = conversations.addQuestion(courseId, conversationId, userId,
                    request.requestId(), request.content());
            if (context.titleChanged()) session.event("conversation.title", Map.of(
                    "conversationId", conversationId, "title", context.conversation().title()));

            long searchStartedAt = System.nanoTime();
            List<KnowledgeEvidence> evidence = knowledge.search(courseId, request.content(), 5);
            if (session.isTerminal()) return;
            AiToolCall knowledgeCall = AiToolCall.succeeded("CourseKnowledgeSearchService",
                    Math.max(0, (System.nanoTime() - searchStartedAt) / 1_000_000));
            int ordinal = 1;
            for (KnowledgeEvidence item : evidence) {
                session.event("citation", CitationView.from(item, ordinal++));
            }
            if (evidence.isEmpty()) {
                session.event("message.delta", Map.of("delta", REFUSAL));
                session.persistAndDone(() -> conversations.addAssistant(courseId, conversationId, userId,
                        REFUSAL, null, List.of()));
                return;
            }

            if (session.isTerminal()) return;
            PromptTemplate prompt = prompts.load("course-qa", "v2");
            String userPrompt = buildPrompt(request.content(), evidence);
            StringBuilder answer = new StringBuilder();
            AiStreamHandle handle = ai.stream(new AiRequest(ModelCapability.REASONING, userId, courseId,
                            prompt.identifier(), prompt.text(), userPrompt,
                            List.of(knowledgeCall), new AiMemoryContext(
                                    courseId + ":" + conversationId + ":" + request.requestId(),
                                    context.history())),
                    delta -> {
                        if (!session.isTerminal()) {
                            answer.append(delta);
                            session.event("message.delta", Map.of("delta", delta));
                        }
                    },
                    response -> complete(courseId, conversationId, userId, answer.toString(),
                            response, evidence, session),
                    error -> session.error("AI_PROVIDER_ERROR", safeMessage(error)));
            session.handle(handle);
        } catch (Throwable error) {
            session.error(error instanceof com.ustb.seforge.common.exception.AppException app
                    ? app.getErrorCode().name() : "REQUEST_FAILED", safeMessage(error));
        }
    }

    private void complete(Long courseId, Long conversationId, Long userId, String answer,
                          AiResponse response, List<KnowledgeEvidence> evidence, StreamSession session) {
        if (session.isTerminal()) return;
        try {
            if (answer.isBlank()) throw new IllegalStateException("Empty answer");
            session.persistAndDone(() -> conversations.addAssistant(courseId, conversationId, userId,
                    answer, response, evidence));
        } catch (RuntimeException error) {
            session.error("PERSISTENCE_ERROR", "Answer could not be saved");
        }
    }

    private String buildPrompt(String question, List<KnowledgeEvidence> evidence) {
        StringBuilder value = new StringBuilder();
        value.append("Course evidence:\n");
        int ordinal = 1;
        for (KnowledgeEvidence item : evidence) {
            String content = item.content().length() <= 2500 ? item.content() : item.content().substring(0, 2500);
            value.append("[C").append(ordinal++).append("] ")
                    .append(item.source()).append(" page ").append(item.page() == null ? "n/a" : item.page())
                    .append(" section ").append(item.section()).append("\n").append(content).append("\n\n");
        }
        return value.append("Question:\n").append(question).toString();
    }

    private String safeMessage(Throwable error) {
        if (error instanceof com.ustb.seforge.common.exception.AppException app) return app.getMessage();
        org.slf4j.LoggerFactory.getLogger(CourseQaStreamService.class)
                .warn("Course QA failed: exception={}", error.getClass().getSimpleName());
        return "当前请求未能完成，请稍后重试。";
    }

    private record SessionKey(Long userId, String requestId) {}

    private final class StreamSession {
        private final SessionKey key;
        private final SseEmitter emitter;
        private final String requestId;
        private final String traceId;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final Object emissionLock = new Object();
        private volatile AiStreamHandle handle;

        private StreamSession(SessionKey key, SseEmitter emitter, String requestId, String traceId) {
            this.key = key;
            this.emitter = emitter;
            this.requestId = requestId;
            this.traceId = traceId;
        }

        void handle(AiStreamHandle value) {
            handle = value;
            if (terminal.get()) value.cancel();
        }

        boolean isTerminal() { return terminal.get(); }

        void event(String type, Object data) {
            boolean disconnected = false;
            synchronized (emissionLock) {
                if (terminal.get()) return;
                try {
                    send(type, data);
                } catch (IOException | IllegalStateException exception) {
                    terminal.set(true);
                    disconnected = true;
                }
            }
            if (disconnected) cancelProvider();
        }

        void persistAndDone(java.util.function.Supplier<MessageView> persist) {
            synchronized (emissionLock) {
                if (terminal.get()) return;
                MessageView message = persist.get();
                terminal.set(true);
                sendTerminalLocked("done", Map.of("message", message));
            }
            emitter.complete();
            active.remove(key, this);
        }

        void error(String code, String message) {
            synchronized (emissionLock) {
                if (!terminal.compareAndSet(false, true)) return;
                sendTerminalLocked("error", Map.of("code", code, "message", message));
            }
            emitter.complete();
            active.remove(key, this);
        }

        void cancel(String code, String message) {
            error(code, message);
            cancelProvider();
        }

        void abort() {
            synchronized (emissionLock) {
                if (!terminal.compareAndSet(false, true)) return;
            }
            cancelProvider();
        }

        private void cancelProvider() {
            // Never acquire the gateway handle lock while holding emissionLock:
            // provider callbacks acquire those locks in the opposite order.
            AiStreamHandle current = handle;
            if (current != null) current.cancel();
            active.remove(key, this);
        }

        private void sendTerminalLocked(String type, Object data) {
            try {
                send(type, data);
            } catch (IOException | IllegalStateException ignored) {
                // The terminal state is already recorded locally; never emit a second terminal event.
            }
        }

        private void send(String type, Object data) throws IOException {
            String eventId = Long.toString(eventSequence.incrementAndGet());
            emitter.send(SseEmitter.event().id(eventId).name(type)
                    .data(new StreamEvent(type, requestId, traceId, eventId, data)));
        }
    }

    private record StreamEvent(String type, String requestId, String traceId, String eventId, Object data) {}
}
