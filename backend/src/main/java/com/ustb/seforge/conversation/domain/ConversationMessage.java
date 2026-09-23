package com.ustb.seforge.conversation.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "conversation_message", indexes = @Index(
        name = "idx_conversation_message_order", columnList = "conversation_id,id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_conversation_message_request",
                columnNames = {"conversation_id", "client_request_id"}))
public class ConversationMessage extends BaseEntity {
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "author_id")
    private Long authorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MessageRole role;
    @Column(nullable = false, columnDefinition = "longtext")
    private String content;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MessageStatus status;
    @Column(name = "client_request_id", length = 64)
    private String clientRequestId;
    @Column(name = "prompt_tokens")
    private Integer promptTokens;
    @Column(name = "completion_tokens")
    private Integer completionTokens;

    protected ConversationMessage() {
    }

    public ConversationMessage(Long conversationId, Long courseId, Long authorId, MessageRole role,
                               String content, String clientRequestId, Integer promptTokens,
                               Integer completionTokens) {
        this.conversationId = conversationId;
        this.courseId = courseId;
        this.authorId = authorId;
        this.role = role;
        this.content = content;
        this.clientRequestId = clientRequestId;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        status = MessageStatus.COMPLETE;
    }

    public Long getConversationId() { return conversationId; }
    public Long getCourseId() { return courseId; }
    public Long getAuthorId() { return authorId; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public MessageStatus getStatus() { return status; }
    public String getClientRequestId() { return clientRequestId; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
}
