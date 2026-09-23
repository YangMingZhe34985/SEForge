package com.ustb.seforge.job.service;

import com.ustb.seforge.job.domain.AsyncJob;
import com.ustb.seforge.job.domain.JobKind;

public record JobSnapshot(Long id, JobKind kind, Long ownerUserId, Long courseId, String payloadJson,
                          int attempt, String workerId) {
    static JobSnapshot from(AsyncJob job) {
        return new JobSnapshot(job.getId(), job.getKind(), job.getOwnerUserId(), job.getCourseId(),
                job.getPayloadJson(), job.getAttempts(), job.getLeaseOwner());
    }
}
