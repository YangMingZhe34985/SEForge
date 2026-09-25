package com.ustb.seforge.assignment.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "grade")
public class Grade extends BaseEntity {
    @Column(name = "submission_id", nullable = false, unique = true)
    private Long submissionId;
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(name = "grader_id")
    private Long graderId;
    @Column(name = "ai_suggested_score", precision = 10, scale = 2)
    private BigDecimal aiSuggestedScore;
    @Column(name = "final_score", precision = 10, scale = 2)
    private BigDecimal finalScore;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GradeStatus status = GradeStatus.PENDING;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "override_reason", columnDefinition = "text")
    private String overrideReason;
    @Column(name = "model_name", length = 128)
    private String modelName;
    @Column(name = "prompt_version", length = 64)
    private String promptVersion;
    @Column(name = "ai_trace_id")
    private Long aiTraceId;

    protected Grade() {
    }

    public Grade(Long submissionId, Long courseId, Long studentId) {
        this.submissionId = submissionId;
        this.courseId = courseId;
        this.studentId = studentId;
    }

    public void applyAiSuggestion(BigDecimal score, String model, String promptVersion) {
        if (status == GradeStatus.CONFIRMED) throw new IllegalStateException("AI cannot replace a confirmed grade");
        aiSuggestedScore = score;
        modelName = model;
        this.promptVersion = promptVersion;
        status = GradeStatus.AI_REVIEWED;
    }

    public void confirm(Long graderId, BigDecimal score, String reason, Instant now) {
        this.graderId = graderId;
        finalScore = score;
        overrideReason = reason;
        confirmedAt = now;
        status = GradeStatus.CONFIRMED;
    }

    public Long getSubmissionId() { return submissionId; }
    public Long getCourseId() { return courseId; }
    public Long getStudentId() { return studentId; }
    public Long getGraderId() { return graderId; }
    public BigDecimal getAiSuggestedScore() { return aiSuggestedScore; }
    public BigDecimal getFinalScore() { return finalScore; }
    public GradeStatus getStatus() { return status; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public String getOverrideReason() { return overrideReason; }
    public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; }
    public Long getAiTraceId() { return aiTraceId; }
    public void linkAiTrace(Long traceId) { this.aiTraceId = traceId; }
}
