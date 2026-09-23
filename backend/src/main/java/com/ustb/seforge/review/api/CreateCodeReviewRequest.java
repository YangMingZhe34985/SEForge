package com.ustb.seforge.review.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCodeReviewRequest(
        @NotNull Long submissionId,
        @NotBlank @Size(max = 512) String attachmentObjectKey,
        @Size(max = 120) String idempotencyKey) {
}
