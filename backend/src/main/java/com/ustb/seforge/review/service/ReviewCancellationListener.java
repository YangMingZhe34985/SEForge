package com.ustb.seforge.review.service;

import com.ustb.seforge.job.service.JobCancellationRequested;
import com.ustb.seforge.review.repository.ReviewJobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReviewCancellationListener {
    private final ReviewJobRepository reviews;

    public ReviewCancellationListener(ReviewJobRepository reviews) {
        this.reviews = reviews;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void cancellationRequested(JobCancellationRequested event) {
        if (!event.kind().name().startsWith("REVIEW_")) return;
        reviews.findByAsyncJobId(event.jobId()).ifPresent(com.ustb.seforge.review.domain.ReviewJob::cancel);
    }
}
