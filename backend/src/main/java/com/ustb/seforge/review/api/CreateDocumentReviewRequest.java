package com.ustb.seforge.review.api;

import jakarta.validation.constraints.Size;

public record CreateDocumentReviewRequest(
        Long documentId,
        Long resourceId,
        @Size(max = 64) String documentKind,
        @Size(max = 120) String idempotencyKey) {
}
