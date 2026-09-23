package com.ustb.seforge.review.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "review_job", indexes = {
        @Index(name = "idx_review_job_course_status", columnList = "course_id,review_type,status"),
        @Index(name = "idx_review_job_submission", columnList = "submission_id")
})
public class ReviewJob extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "async_job_id", unique = true)
    private Long asyncJobId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 32)
    private ReviewType reviewType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReviewJobStatus status;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "config", columnDefinition = "json")
    private String configJson;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected ReviewJob() {
    }

    public ReviewJob(Long courseId, Long assignmentId, Long submissionId, Long documentId,
                     Long resourceId, Long requestedBy, ReviewType reviewType, String objectKey,
                     String configJson) {
        this.courseId = courseId;
        this.assignmentId = assignmentId;
        this.submissionId = submissionId;
        this.documentId = documentId;
        this.resourceId = resourceId;
        this.requestedBy = requestedBy;
        this.reviewType = reviewType;
        this.objectKey = objectKey;
        this.configJson = configJson == null || configJson.isBlank() ? "{}" : configJson;
        this.status = ReviewJobStatus.QUEUED;
    }

    public void attachAsyncJob(Long asyncJobId) {
        if (asyncJobId == null) throw new IllegalArgumentException("asyncJobId is required");
        this.asyncJobId = asyncJobId;
        this.status = ReviewJobStatus.QUEUED;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void start() {
        status = ReviewJobStatus.PROCESSING;
        errorCode = null;
        errorMessage = null;
    }

    public void complete() {
        status = ReviewJobStatus.COMPLETED;
        errorCode = null;
        errorMessage = null;
    }

    public void cancel() {
        if (status == ReviewJobStatus.COMPLETED) return;
        status = ReviewJobStatus.CANCELLED;
        errorCode = "CANCELLED";
        errorMessage = "Review was cancelled";
    }

    public void fail(String code, Throwable failure) {
        if (status == ReviewJobStatus.CANCELLED) return;
        status = ReviewJobStatus.FAILED;
        errorCode = code == null || code.isBlank() ? "REVIEW_FAILED" : code;
        String message = failure == null || failure.getMessage() == null
                ? "Review failed" : failure.getMessage();
        errorMessage = message.substring(0, Math.min(message.length(), 4000));
    }

    public Long getCourseId() { return courseId; }
    public Long getAssignmentId() { return assignmentId; }
    public Long getSubmissionId() { return submissionId; }
    public Long getDocumentId() { return documentId; }
    public Long getResourceId() { return resourceId; }
    public Long getAsyncJobId() { return asyncJobId; }
    public Long getRequestedBy() { return requestedBy; }
    public ReviewType getReviewType() { return reviewType; }
    public ReviewJobStatus getStatus() { return status; }
    public String getObjectKey() { return objectKey; }
    public String getConfigJson() { return configJson; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
