package com.ustb.seforge.conversation.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.conversation.api.*;
import com.ustb.seforge.conversation.domain.ConversationGeneration;
import com.ustb.seforge.conversation.repository.*;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationGenerationService {
    private final ConversationService conversations;
    private final ConversationRepository conversationRows;
    private final ConversationGenerationRepository generations;
    private final ConversationErasureService erasure;

    public ConversationGenerationService(ConversationService conversations, ConversationRepository conversationRows,
                                         ConversationGenerationRepository generations, ConversationErasureService erasure) {
        this.conversations = conversations; this.conversationRows = conversationRows; this.generations = generations;
        this.erasure=erasure;
    }

    @Transactional
    public ConversationService.QuestionContext begin(Long course, Long conversation, Long owner,
                                                     AskQuestionRequest request, String traceId) {
        // Lock before loading the entity into Hibernate's identity map. Upgrading a stale
        // read to a write lock would throw instead of rejecting the concurrent request cleanly.
        conversationRows.lockOwned(conversation, owner, course)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Conversation not found"));
        conversations.require(course, conversation, owner);
        var latest = generations.findFirstByConversationIdOrderByIdDesc(conversation).orElse(null);
        if (latest != null && latest.processing()) {
            throw new AppException(ErrorCode.CONFLICT, "当前会话正在生成，请恢复状态或显式取消后重试");
        }
        // addQuestion's durable unique request check also covers completed/failed replays.
        var context = conversations.addQuestion(course, conversation, owner, request.requestId(), request.content());
        generations.save(new ConversationGeneration(conversation, request.requestId(), request.content(), traceId));
        return context;
    }

    @Transactional
    public GenerationView latest(Long course, Long conversation, Long owner) {
        conversations.require(course, conversation, owner);
        var value = generations.findTopByConversationIdOrderByIdDesc(conversation).orElse(null);
        if (value == null) return null;
        expire(value);
        return GenerationView.from(value);
    }

    @Transactional
    public void deleteConversation(Long course, Long conversation, Long owner) {
        var row=conversationRows.lockOwned(conversation,owner,course)
                .orElseThrow(()->new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Conversation not found"));
        conversations.require(course,conversation,owner);
        var latest=generations.findFirstByConversationIdOrderByIdDesc(conversation).orElse(null);
        if(latest!=null && latest.processing())throw new AppException(ErrorCode.CONFLICT,"会话正在生成，请先取消生成再删除");
        erasure.erase(course,conversation);
    }

    @Transactional
    public MessageView complete(Long conversation, String requestId, Supplier<MessageView> persist) {
        var value = generations.findByConversationIdAndRequestId(conversation, requestId).orElseThrow();
        expire(value);
        if (!value.processing()) return null;
        // Message, citation snapshots and COMPLETED commit together under the same row lock.
        MessageView message = persist.get();
        value.completed(message.id());
        return message;
    }

    @Transactional
    public boolean fail(Long conversation, String requestId, String code, String message) {
        var value = generations.findByConversationIdAndRequestId(conversation, requestId).orElse(null);
        if (value == null || !value.processing()) return false;
        value.failed(code, message);
        return true;
    }

    @Transactional
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 15_000)
    public void expireAbandoned() {
        for (var id : generations.expiredIds(
                ConversationGeneration.Status.PROCESSING, Instant.now())) {
            generations.lockById(id).ifPresent(this::expire);
        }
    }

    private void expire(ConversationGeneration value) {
        if (value.processing() && !value.getDeadlineAt().isAfter(Instant.now())) {
            value.failed("GENERATION_TIMEOUT", "生成超时或服务中断，请重试；未完成的回答不会保存。");
        }
    }
}
