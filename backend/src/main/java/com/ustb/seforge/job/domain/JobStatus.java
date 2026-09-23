package com.ustb.seforge.job.domain;

public enum JobStatus {
    PENDING,
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    COMPLETED,
    FAILED,
    CANCELLED,
    DEAD_LETTER;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == DEAD_LETTER;
    }
}
