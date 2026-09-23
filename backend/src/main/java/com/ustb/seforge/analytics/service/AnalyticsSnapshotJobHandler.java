package com.ustb.seforge.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.service.JobHandler;
import com.ustb.seforge.job.service.JobSnapshot;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.job.service.JobExecutionAbortedException;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsSnapshotJobHandler implements JobHandler {
    private final AnalyticsService analytics;
    private final ObjectMapper objectMapper;
    private final AsyncJobService jobs;

    public AnalyticsSnapshotJobHandler(AnalyticsService analytics, ObjectMapper objectMapper,
                                       AsyncJobService jobs) {
        this.analytics = analytics;
        this.objectMapper = objectMapper;
        this.jobs = jobs;
    }

    @Override
    public JobKind kind() {
        return JobKind.ANALYTICS_SNAPSHOT;
    }

    @Override
    public String handle(JobSnapshot job) throws Exception {
        if (!jobs.isExecutionActive(job.id(), job.workerId())) {
            throw new JobExecutionAbortedException("Analytics snapshot was cancelled or its lease was lost");
        }
        AnalyticsService.SnapshotRequest request = objectMapper.readValue(
                job.payloadJson(), AnalyticsService.SnapshotRequest.class);
        return jobs.completeAtomically(job.id(), job.workerId(),
                () -> analytics.generate(job.courseId(), request.classId(), request.requestedAt()));
    }
}
