package com.ustb.seforge.conversation.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "answer_feedback", uniqueConstraints = @UniqueConstraint(
        name = "uk_answer_feedback_user", columnNames = {"message_id", "user_id"}))
public class AnswerFeedback extends BaseEntity {
    @Column(name = "message_id", nullable = false)
    private Long messageId;
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackRating rating;
    @Column(columnDefinition = "text")
    private String comment;

    protected AnswerFeedback() {}

    public AnswerFeedback(Long messageId, Long courseId, Long userId, FeedbackRating rating, String comment) {
        this.messageId = messageId;
        this.courseId = courseId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
    }

    public void update(FeedbackRating rating, String comment) { this.rating = rating; this.comment = comment; }
}
