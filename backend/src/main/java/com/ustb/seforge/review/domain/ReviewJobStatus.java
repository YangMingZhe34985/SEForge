package com.ustb.seforge.review.domain;

public enum ReviewJobStatus {
    QUEUED,
    PROCESSING,
    RETRY_WAIT,
    COMPLETED,
    FAILED,
    CANCELLED
}
