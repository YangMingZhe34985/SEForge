package com.ustb.seforge.conversation.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;

/** Durable request state, never a buffer for partially generated answers. */
@Entity
@Table(name = "conversation_generation")
public class ConversationGeneration extends BaseEntity {
    public enum Status { PROCESSING, COMPLETED, FAILED, CANCELLED }
    @Column(name = "conversation_id", nullable = false) private Long conversationId;
    @Column(name = "request_id", nullable = false, length = 64) private String requestId;
    @Column(name = "trace_id", nullable = false, length = 64) private String traceId;
    @Column(nullable = false, columnDefinition = "text") private String question;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Column(name = "deadline_at", nullable = false) private Instant deadlineAt;
    @Column(name = "assistant_message_id") private Long assistantMessageId;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "error_message", length = 1000) private String errorMessage;

    protected ConversationGeneration() {}
    public ConversationGeneration(Long conversationId, String requestId, String question, String traceId) {
        this.conversationId = conversationId;
        this.requestId = requestId;
        this.question = question;
        this.traceId = traceId;
        status = Status.PROCESSING;
        deadlineAt = Instant.now().plusSeconds(600);
    }
    public boolean processing() { return status == Status.PROCESSING; }
    public void completed(Long messageId) { status = Status.COMPLETED; assistantMessageId = messageId; }
    public void failed(String code, String message) {
        status = "CANCELLED".equals(code) ? Status.CANCELLED : Status.FAILED;
        errorCode = code; errorMessage = message;
    }
    public Long getConversationId() { return conversationId; }
    public String getRequestId() { return requestId; }
    public String getQuestion() { return question; }
    public String getTraceId() { return traceId; }
    public Status getStatus() { return status; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public Long getAssistantMessageId() { return assistantMessageId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
