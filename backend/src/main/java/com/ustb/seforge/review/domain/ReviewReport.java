package com.ustb.seforge.review.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "review_report",
        indexes = @Index(name = "idx_review_report_course", columnList = "course_id,generated_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_review_report_job", columnNames = "review_job_id"))
public class ReviewReport extends BaseEntity {
    @Column(name = "ai_trace_id")
    private Long aiTraceId;
    public Long getAiTraceId() { return aiTraceId; }
    public void linkAiTrace(Long traceId) { this.aiTraceId = traceId; }
    @Column(name = "review_job_id", nullable = false)
    private Long reviewJobId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReviewReportStatus status;

    @Column(columnDefinition = "longtext")
    private String summary;

    @Column(name = "structured_result", nullable = false, columnDefinition = "json")
    private String structuredResultJson;

    @Column(name = "export_object_key", length = 512)
    private String exportObjectKey;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Column(name = "generated_at")
    private Instant generatedAt;

    protected ReviewReport() {
    }

    public ReviewReport(Long reviewJobId, Long courseId, String summary, String structuredResultJson,
                        String modelName, String promptVersion) {
        this.reviewJobId = reviewJobId;
        this.courseId = courseId;
        this.summary = summary;
        this.structuredResultJson = structuredResultJson;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.status = ReviewReportStatus.COMPLETED;
        this.generatedAt = Instant.now();
    }

    public Long getReviewJobId() { return reviewJobId; }
    public Long getCourseId() { return courseId; }
    public ReviewReportStatus getStatus() { return status; }
    public String getSummary() { return summary; }
    public String getStructuredResultJson() { return structuredResultJson; }
    public String getExportObjectKey() { return exportObjectKey; }
    public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; }
    public Instant getGeneratedAt() { return generatedAt; }
}
