package com.ustb.seforge.job.service;

import com.ustb.seforge.job.domain.JobKind;

public record JobCancellationRequested(Long jobId, JobKind kind, Long courseId) {
}
