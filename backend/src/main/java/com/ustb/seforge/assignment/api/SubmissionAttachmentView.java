package com.ustb.seforge.assignment.api;

public record SubmissionAttachmentView(
        Long submissionId,
        Long questionId,
        String objectKey,
        String fileName,
        long sizeBytes,
        String contentType) {
}
