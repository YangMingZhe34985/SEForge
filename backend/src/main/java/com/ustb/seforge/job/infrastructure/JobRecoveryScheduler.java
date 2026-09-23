package com.ustb.seforge.job.infrastructure;

import com.ustb.seforge.job.service.AsyncJobService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seforge.jobs", name = "enabled", havingValue = "true")
public class JobRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(JobRecoveryScheduler.class);
    private final AsyncJobService jobs;

    public JobRecoveryScheduler(AsyncJobService jobs) {
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${seforge.jobs.recovery-interval:30000}")
    public void recover() {
        int recovered = jobs.recoverStalled(Instant.now());
        if (recovered > 0) log.info("Recovered {} stalled async job(s)", recovered);
    }
}
