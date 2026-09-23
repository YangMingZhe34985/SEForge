package com.ustb.seforge.review.domain;

import com.ustb.seforge.job.domain.JobKind;

public enum ReviewType {
    DOCUMENT(JobKind.REVIEW_DOCUMENT),
    ASSIGNMENT(JobKind.REVIEW_ASSIGNMENT),
    CODE(JobKind.REVIEW_CODE);

    private final JobKind jobKind;

    ReviewType(JobKind jobKind) {
        this.jobKind = jobKind;
    }

    public JobKind jobKind() {
        return jobKind;
    }
}
