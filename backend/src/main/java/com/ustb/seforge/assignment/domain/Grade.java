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
    @Column(name = "rule_suggested_score", precision = 10, scale = 2)
    private BigDecimal ruleSuggestedScore;
    @Column(name = "final_score", precision = 10, scale = 2)
    private BigDecimal finalScore;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GradeStatus status = GradeStatus.WAITING_REVIEW;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "published_by")
    private Long publishedBy;
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
        if (isFinal()) throw new IllegalStateException("AI cannot replace a confirmed grade");
        aiSuggestedScore = score;
        modelName = model;
        this.promptVersion = promptVersion;
        status = GradeStatus.PENDING_CONFIRMATION;
    }

    public void confirm(Long graderId, BigDecimal score, String reason, Instant now) {
        this.graderId = graderId;
        finalScore = score;
        overrideReason = reason;
        confirmedAt = now;
        status = GradeStatus.CONFIRMED;
    }

    public void applyRuleSuggestion(BigDecimal score, boolean complete) {
        if(isFinal())throw new IllegalStateException("Cannot replace a confirmed grade");
        ruleSuggestedScore=score;
        if(complete){aiSuggestedScore=null;modelName="RULE";promptVersion="objective:v1";status=GradeStatus.PENDING_CONFIRMATION;}
        else if (status == GradeStatus.WAITING_REVIEW) status = GradeStatus.REVIEWED;
    }

    public void applyMixedSuggestion(BigDecimal total, BigDecimal ruleTotal, String model, String promptVersion, boolean hasAi) {
        if(hasAi)applyAiSuggestion(total.subtract(ruleTotal),model,promptVersion);
        applyRuleSuggestion(ruleTotal,!hasAi);
    }

    public BigDecimal getRuleSuggestedScore(){return ruleSuggestedScore;}
    public BigDecimal getSuggestedScore(){return aiSuggestedScore==null&&ruleSuggestedScore==null?null:
            (aiSuggestedScore==null?BigDecimal.ZERO:aiSuggestedScore).add(ruleSuggestedScore==null?BigDecimal.ZERO:ruleSuggestedScore);}

    public Long getSubmissionId() { return submissionId; }
    public boolean isFinal() { return status == GradeStatus.CONFIRMED || status == GradeStatus.PUBLISHED; }
    public void manuallyReviewed() { if (isFinal()) throw new IllegalStateException("Final grade is immutable"); status = GradeStatus.PENDING_CONFIRMATION; }
    public void publish(Long actor, Instant now) {
        if (status != GradeStatus.CONFIRMED) throw new IllegalStateException("Only confirmed grades can be published");
        publishedBy = actor; publishedAt = now; status = GradeStatus.PUBLISHED;
    }
    public Instant getPublishedAt() { return publishedAt; }
    public Long getPublishedBy() { return publishedBy; }
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
