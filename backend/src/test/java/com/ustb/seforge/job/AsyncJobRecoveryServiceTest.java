package com.ustb.seforge.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.job.domain.AsyncJob;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.domain.JobStatus;
import com.ustb.seforge.job.domain.OutboxEvent;
import com.ustb.seforge.job.domain.OutboxStatus;
import com.ustb.seforge.job.repository.AsyncJobRepository;
import com.ustb.seforge.job.repository.OutboxEventRepository;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.job.service.JobEventBroker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AsyncJobRecoveryServiceTest {
    @Mock AsyncJobRepository jobs;
    @Mock OutboxEventRepository outbox;
    @Mock JobEventBroker events;
    @Mock ApplicationEventPublisher eventPublisher;

    private AsyncJobService service;

    @BeforeEach
    void setUp() {
        SEForgeProperties properties = new SEForgeProperties();
        properties.getJobs().setQueuedStaleAfter(Duration.ofMinutes(2));
        properties.getJobs().setRecoveryBatchSize(25);
        service = new AsyncJobService(jobs, outbox, new ObjectMapper(), properties, events, eventPublisher);
    }

    @Test
    void redisLossRecreatesDispatchForStaleQueuedJob() {
        Instant now = Instant.parse("2026-09-22T00:10:00Z");
        AsyncJob job = job(41L, 3);
        job.markQueued();
        when(jobs.findRecoverable(eq(now), eq(now.minusSeconds(120)), any(Pageable.class)))
                .thenReturn(List.of(job));
        when(outbox.existsByAggregateTypeAndAggregateIdAndEventTypeAndStatus(
                "AsyncJob", 41L, "job.requested", OutboxStatus.PENDING)).thenReturn(false);

        assertThat(service.recoverStalled(now)).isEqualTo(1);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        verify(outbox).save(any(OutboxEvent.class));
    }

    @Test
    void expiredFinalLeaseMovesToDeadLetterWithoutRedisRedelivery() {
        Instant now = Instant.parse("2026-09-22T00:10:00Z");
        AsyncJob job = job(42L, 1);
        assertThat(job.claim("lost-worker", now.minusSeconds(30), now.minusSeconds(1))).isTrue();
        when(jobs.findRecoverable(eq(now), eq(now.minusSeconds(120)), any(Pageable.class)))
                .thenReturn(List.of(job));

        assertThat(service.recoverStalled(now)).isZero();

        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD_LETTER);
        verify(outbox, never()).save(any());
    }

    private AsyncJob job(Long id, int maxAttempts) {
        AsyncJob job = new AsyncJob(JobKind.REVIEW_DOCUMENT, 7L, 3L, "{}", "key-" + id, maxAttempts);
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }
}
