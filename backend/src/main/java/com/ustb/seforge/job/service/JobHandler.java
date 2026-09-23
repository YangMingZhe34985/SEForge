package com.ustb.seforge.job.service;

import com.ustb.seforge.job.domain.JobKind;

public interface JobHandler {
    JobKind kind();

    String handle(JobSnapshot job) throws Exception;
}
