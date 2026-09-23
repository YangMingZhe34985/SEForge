package com.ustb.seforge.job.api;

import com.ustb.seforge.job.domain.AsyncJob;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.domain.JobStatus;
import java.time.Instant;

public record AsyncJobView(
        Long id,
        JobKind type,
        JobStatus status,
        Long courseId,
        int attempts,
        int maxAttempts,
        boolean cancelRequested,
        String result,
        String error,
        Instant createdAt,
        Instant updatedAt) {
    public static AsyncJobView from(AsyncJob job) {
        return new AsyncJobView(job.getId(), job.getKind(), job.getStatus(), job.getCourseId(),
                job.getAttempts(), job.getMaxAttempts(), job.isCancelRequested(),
                job.getResultJson(), job.getLastError(), job.getCreatedAt(), job.getUpdatedAt());
    }
}
