package com.ustb.seforge.review.api;

import jakarta.validation.constraints.Size;

public record CreateDocumentReviewRequest(
        Long documentId,
        Long resourceId,
        @Size(max = 64) String documentKind,
        @Size(max = 120) String idempotencyKey,
        Long submissionId, Long questionId, Long mediaId, Long artifactId) {
    public CreateDocumentReviewRequest(Long documentId, Long resourceId,String kind,String key){this(documentId,resourceId,kind,key,null,null,null,null);}
}
