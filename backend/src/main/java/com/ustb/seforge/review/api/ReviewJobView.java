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
}
