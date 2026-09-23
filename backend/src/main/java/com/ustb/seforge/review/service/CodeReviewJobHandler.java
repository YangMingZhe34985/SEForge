package com.ustb.seforge.review.service;

import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.service.JobHandler;
import com.ustb.seforge.job.service.JobSnapshot;
import com.ustb.seforge.review.domain.ReviewType;
import org.springframework.stereotype.Component;

@Component
public class CodeReviewJobHandler implements JobHandler {
    private final ReviewExecutionService reviews;

    public CodeReviewJobHandler(ReviewExecutionService reviews) {
        this.reviews = reviews;
    }

    @Override
    public JobKind kind() {
        return JobKind.REVIEW_CODE;
    }

    @Override
    public String handle(JobSnapshot job) throws Exception {
        try {
            return reviews.execute(job, ReviewType.CODE);
        } catch (Exception exception) {
            reviews.markFailed(job.id(), exception);
            throw exception;
        }
    }
}
