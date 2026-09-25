package com.ustb.seforge.review.api;

import com.ustb.seforge.review.domain.ReviewJob;
import com.ustb.seforge.review.domain.ReviewJobStatus;
import com.ustb.seforge.review.domain.ReviewType;
import java.time.Instant;

public record ReviewJobView(
        Long id,
        Long courseId,
        Long assignmentId,
        Long submissionId,
        Long documentId,
        Long resourceId,
        Long asyncJobId,
        ReviewType type,
        ReviewJobStatus status,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {
    public static ReviewJobView from(ReviewJob job) {
        return new ReviewJobView(job.getId(), job.getCourseId(), job.getAssignmentId(),
                job.getSubmissionId(), job.getDocumentId(), job.getResourceId(), job.getAsyncJobId(),
                job.getReviewType(), job.getStatus(), job.getErrorCode(), job.getErrorMessage(),
                job.getCreatedAt(), job.getUpdatedAt());
    }

    /** The durable runtime is authoritative even when rejection happens before the handler. */
    public static ReviewJobView from(ReviewJob job, com.ustb.seforge.job.api.AsyncJobView execution) {
        if (execution == null) return from(job);
        ReviewJobStatus status = switch (execution.status()) {
            case PENDING, QUEUED -> ReviewJobStatus.QUEUED;
            case RUNNING -> ReviewJobStatus.PROCESSING;
            case RETRY_WAIT -> ReviewJobStatus.RETRY_WAIT;
            case COMPLETED -> ReviewJobStatus.COMPLETED;
            case CANCELLED -> ReviewJobStatus.CANCELLED;
            case FAILED, DEAD_LETTER -> ReviewJobStatus.FAILED;
        };
        boolean failure = status == ReviewJobStatus.FAILED || status == ReviewJobStatus.RETRY_WAIT;
        return new ReviewJobView(job.getId(), job.getCourseId(), job.getAssignmentId(), job.getSubmissionId(),
                job.getDocumentId(), job.getResourceId(), job.getAsyncJobId(), job.getReviewType(), status,
                failure ? (job.getErrorCode() == null ? "REVIEW_FAILED" : job.getErrorCode()) : null,
                failure ? (job.getErrorMessage() == null ? "Review execution failed; verify permissions and dependencies" : job.getErrorMessage()) : null,
                job.getCreatedAt(), execution.updatedAt());
    }
}
