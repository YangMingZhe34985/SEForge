package com.ustb.seforge.conversation.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversation", indexes = @Index(
        name = "idx_conversation_owner_course", columnList = "owner_id,course_id,updated_at"))
public class Conversation extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(columnDefinition = "text")
    private String summary;
    @Column(name = "summary_through_message_id")
    private Long summaryThroughMessageId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConversationStatus status;
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    protected Conversation() {
    }

    public Conversation(Long courseId, Long ownerId, String title) {
        this.courseId = courseId;
        this.ownerId = ownerId;
        this.title = title;
        this.status = ConversationStatus.ACTIVE;
    }

    public void messageAdded() { lastMessageAt = Instant.now(); }
    public void rename(String title) { this.title = title; }
    public void archive() { status = ConversationStatus.ARCHIVED; }
    public void updateSummary(String summary, Long throughMessageId) {
        if (throughMessageId != null && summaryThroughMessageId != null
                && throughMessageId < summaryThroughMessageId) {
            throw new IllegalArgumentException("Conversation summary watermark cannot move backwards");
        }
        this.summary = summary == null || summary.isBlank() ? null : summary.trim();
        this.summaryThroughMessageId = throughMessageId;
    }

    public Long getCourseId() { return courseId; }
    public Long getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public Long getSummaryThroughMessageId() { return summaryThroughMessageId; }
    public ConversationStatus getStatus() { return status; }
    public Instant getLastMessageAt() { return lastMessageAt; }
}
