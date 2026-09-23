package com.ustb.seforge.conversation.service;

import com.ustb.seforge.ai.application.AiResponse;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.content.service.KnowledgeEvidence;
import com.ustb.seforge.conversation.api.CitationView;
import com.ustb.seforge.conversation.api.ConversationView;
import com.ustb.seforge.conversation.api.FeedbackRequest;
import com.ustb.seforge.conversation.api.MessageView;
import com.ustb.seforge.conversation.domain.AnswerFeedback;
import com.ustb.seforge.conversation.domain.Conversation;
import com.ustb.seforge.conversation.domain.ConversationMessage;
import com.ustb.seforge.conversation.domain.ConversationStatus;
import com.ustb.seforge.conversation.domain.MessageCitation;
import com.ustb.seforge.conversation.domain.MessageRole;
import com.ustb.seforge.conversation.repository.AnswerFeedbackRepository;
import com.ustb.seforge.conversation.repository.ConversationMessageRepository;
import com.ustb.seforge.conversation.repository.ConversationRepository;
import com.ustb.seforge.conversation.repository.MessageCitationRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {
    private static final String DEFAULT_TITLE = "New conversation";

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final MessageCitationRepository citations;
    private final AnswerFeedbackRepository feedback;
    private final CourseAccessService access;
    private final ConversationContextBuilder contextBuilder = new ConversationContextBuilder();

    public ConversationService(ConversationRepository conversations, ConversationMessageRepository messages,
                               MessageCitationRepository citations, AnswerFeedbackRepository feedback,
                               CourseAccessService access) {
        this.conversations = conversations;
        this.messages = messages;
        this.citations = citations;
        this.feedback = feedback;
        this.access = access;
    }

    @Transactional
    public ConversationView create(Long courseId, Long ownerId, String requestedTitle) {
        access.requireMember(courseId, ownerId);
        String title = requestedTitle == null || requestedTitle.isBlank() ? DEFAULT_TITLE : requestedTitle.trim();
        return ConversationView.from(conversations.save(new Conversation(courseId, ownerId, title)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationView> list(Long courseId, Long ownerId, int page, int size) {
        access.requireMember(courseId, ownerId);
        Page<ConversationView> result = conversations.findAllByOwnerIdAndCourseIdAndStatusOrderByUpdatedAtDesc(
                ownerId, courseId, ConversationStatus.ACTIVE, pageRequest(page, size))
                .map(ConversationView::from);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageView> messages(Long courseId, Long conversationId, Long ownerId,
                                               int page, int size) {
        require(courseId, conversationId, ownerId);
        Page<MessageView> result = messages.findAllByConversationIdOrderByIdAsc(
                        conversationId, pageRequest(page, size))
                .map(message -> MessageView.from(message,
                        citations.findAllByMessageIdOrderByOrdinalAsc(message.getId()).stream()
                                .map(CitationView::from).toList()));
        return PageResponse.from(result);
    }

    @Transactional
    public QuestionContext addQuestion(Long courseId, Long conversationId, Long ownerId,
                                       String requestId, String question) {
        access.requireMember(courseId, ownerId);
        Conversation conversation = require(courseId, conversationId, ownerId);
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new AppException(ErrorCode.CONFLICT, "Conversation is archived");
        }
        if (messages.findByConversationIdAndClientRequestId(conversationId, requestId).isPresent()) {
            throw new AppException(ErrorCode.CONFLICT, "requestId has already been used");
        }
        String history = history(conversation);
        ConversationMessage message = messages.save(new ConversationMessage(conversationId, courseId, ownerId,
                MessageRole.USER, question.trim(), requestId, null, null));
        conversation.messageAdded();
        boolean titleChanged = DEFAULT_TITLE.equals(conversation.getTitle());
        if (titleChanged) conversation.rename(title(question));
        return new QuestionContext(ConversationView.from(conversation), message.getId(), history, titleChanged);
    }

    @Transactional
    public MessageView addAssistant(Long courseId, Long conversationId, Long ownerId, String answer,
                                    AiResponse ai, List<KnowledgeEvidence> evidence) {
        Conversation conversation = require(courseId, conversationId, ownerId);
        ConversationMessage message = messages.save(new ConversationMessage(conversationId, courseId, null,
                MessageRole.ASSISTANT, answer, null, ai == null ? null : ai.inputTokens(),
                ai == null ? null : ai.outputTokens()));
        List<MessageCitation> saved = new ArrayList<>();
        int ordinal = 1;
        for (KnowledgeEvidence item : evidence) {
            CitationView citation = CitationView.from(item, ordinal);
            saved.add(citations.save(new MessageCitation(message.getId(), courseId, item.documentId(),
                    item.chunkId(), ordinal, citation.label(), item.source(), item.page(), citation.chapter(),
                    item.section(), citation.quote(), item.score())));
            ordinal++;
        }
        conversation.messageAdded();
        return MessageView.from(message, saved.stream().map(CitationView::from).toList());
    }

    @Transactional
    public void archive(Long courseId, Long conversationId, Long ownerId) {
        require(courseId, conversationId, ownerId).archive();
    }

    @Transactional
    public void feedback(Long courseId, Long conversationId, Long messageId, Long ownerId,
                         FeedbackRequest request) {
        require(courseId, conversationId, ownerId);
        ConversationMessage message = messages.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Message not found"));
        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Only assistant answers can be rated");
        }
        AnswerFeedback value = feedback.findByMessageIdAndUserId(messageId, ownerId)
                .orElseGet(() -> new AnswerFeedback(messageId, courseId, ownerId,
                        request.rating(), trim(request.comment())));
        value.update(request.rating(), trim(request.comment()));
        feedback.save(value);
    }

    @Transactional(readOnly = true)
    public Conversation require(Long courseId, Long conversationId, Long ownerId) {
        return conversations.findByIdAndOwnerIdAndCourseId(conversationId, ownerId, courseId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Conversation not found"));
    }

    private String history(Conversation conversation) {
        Long watermark = conversation.getSummaryThroughMessageId();
        List<ConversationMessage> unsummarized =
                messages.findByConversationIdAndIdGreaterThanOrderByIdAsc(
                        conversation.getId(), watermark == null ? 0L : watermark);
        ConversationContextBuilder.ContextWindow context = contextBuilder.build(
                conversation.getSummary(), watermark, unsummarized);
        if (!Objects.equals(conversation.getSummary(), context.summary())
                || !Objects.equals(watermark, context.summarizedThroughMessageId())) {
            conversation.updateSummary(context.summary(), context.summarizedThroughMessageId());
        }
        return context.history();
    }

    private String title(String question) {
        String compact = question.trim().replaceAll("\\s+", " ");
        return compact.length() <= 60 ? compact : compact.substring(0, 60) + "…";
    }

    private String trim(String value) { return value == null ? null : value.trim(); }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
    }

    public record QuestionContext(ConversationView conversation, Long userMessageId, String history,
                                  boolean titleChanged) {}
}
