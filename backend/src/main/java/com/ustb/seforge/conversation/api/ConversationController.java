package com.ustb.seforge.conversation.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.conversation.service.ConversationService;
import com.ustb.seforge.conversation.service.CourseQaStreamService;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/conversations")
public class ConversationController {
    private final ConversationService conversations;
    private final CourseQaStreamService streams;
    private final com.ustb.seforge.conversation.service.ConversationGenerationService generations;

    public ConversationController(ConversationService conversations, CourseQaStreamService streams,
                                  com.ustb.seforge.conversation.service.ConversationGenerationService generations) {
        this.conversations = conversations;
        this.streams = streams;
        this.generations = generations;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(
            org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void disconnectedClient() {
        // A disconnected presentation socket does not cancel durable generation:
        // do not ask the global JSON error handler to write a second response.
    }

    @PostMapping
    public ApiEnvelope<ConversationView> create(@PathVariable Long courseId,
                                                @Valid @RequestBody CreateConversationRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(conversations.create(courseId, principal.userId(), request.title()));
    }

    @GetMapping
    public ApiEnvelope<PageResponse<ConversationView>> list(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(conversations.list(courseId, principal.userId(), page, size));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiEnvelope<PageResponse<MessageView>> messages(
            @PathVariable Long courseId,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(conversations.messages(
                courseId, conversationId, principal.userId(), page, size));
    }

    @PostMapping(value = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@PathVariable Long courseId, @PathVariable Long conversationId,
                          @Valid @RequestBody AskQuestionRequest request,
                          @AuthenticationPrincipal UserPrincipal principal) {
        conversations.require(courseId, conversationId, principal.userId());
        return streams.open(courseId, conversationId, principal.userId(), request);
    }

    @DeleteMapping("/{conversationId}")
    public ApiEnvelope<Void> archive(@PathVariable Long courseId, @PathVariable Long conversationId,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        generations.deleteConversation(courseId, conversationId, principal.userId());
        return ApiEnvelope.success("Conversation and associated content deleted", null);
    }

    public record RenameRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=255) String title){}
    @org.springframework.web.bind.annotation.PutMapping("/{conversationId}")
    public ApiEnvelope<ConversationView> rename(@PathVariable Long courseId,@PathVariable Long conversationId,
            @AuthenticationPrincipal UserPrincipal principal,@Valid @RequestBody RenameRequest request){
        return ApiEnvelope.success(conversations.rename(courseId,conversationId,principal.userId(),request.title()));
    }

    @DeleteMapping("/{conversationId}/requests/{requestId}")
    public ApiEnvelope<Map<String, Boolean>> cancel(@PathVariable Long courseId,
                                                    @PathVariable Long conversationId,
                                                    @PathVariable String requestId,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        conversations.require(courseId, conversationId, principal.userId());
        return ApiEnvelope.success(Map.of("cancelled", streams.cancel(courseId, conversationId, principal.userId(), requestId)));
    }

    @GetMapping("/{conversationId}/generation")
    public ApiEnvelope<GenerationView> generation(@PathVariable Long courseId, @PathVariable Long conversationId,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(generations.latest(courseId, conversationId, principal.userId()));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/feedback")
    public ApiEnvelope<Void> feedback(@PathVariable Long courseId, @PathVariable Long conversationId,
                                      @PathVariable Long messageId,
                                      @Valid @RequestBody FeedbackRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        conversations.feedback(courseId, conversationId, messageId, principal.userId(), request);
        return ApiEnvelope.success("Feedback saved", null);
    }
}
