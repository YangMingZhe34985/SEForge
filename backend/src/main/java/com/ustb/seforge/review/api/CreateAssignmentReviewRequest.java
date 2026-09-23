package com.ustb.seforge.review.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAssignmentReviewRequest(
        @NotNull Long submissionId,
        @Size(max = 120) String idempotencyKey) {
}
