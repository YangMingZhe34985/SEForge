package com.ustb.seforge.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.job.domain.AsyncJob;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.domain.JobStatus;
import com.ustb.seforge.job.repository.AsyncJobRepository;
import com.ustb.seforge.job.repository.OutboxEventRepository;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.job.service.JobEventBroker;
import com.ustb.seforge.job.service.JobExecutionAbortedException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AsyncJobCompletionServiceTest {

    @Test
    void cancellationWinsBeforeTheDomainCommitStarts() {
        AsyncJobRepository repository = mock(AsyncJobRepository.class);
        AsyncJob job = runningJob();
        job.requestCancel();
        when(repository.findForUpdate(41L)).thenReturn(Optional.of(job));
        AsyncJobService service = service(repository);
        AtomicBoolean domainCommitted = new AtomicBoolean();

        assertThatThrownBy(() -> service.completeAtomically(41L, "worker-1", () -> {
            domainCommitted.set(true);
            return "{}";
        })).isInstanceOf(JobExecutionAbortedException.class);

        assertThat(domainCommitted).isFalse();
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void successfulDomainCommitAndJobCompletionShareOneFence() {
        AsyncJobRepository repository = mock(AsyncJobRepository.class);
        AsyncJob job = runningJob();
        when(repository.findForUpdate(41L)).thenReturn(Optional.of(job));
        AsyncJobService service = service(repository);
        AtomicBoolean domainCommitted = new AtomicBoolean();

        String result = service.completeAtomically(41L, "worker-1", () -> {
            domainCommitted.set(true);
            return "{\"ok\":true}";
        });

        assertThat(result).isEqualTo("{\"ok\":true}");
        assertThat(domainCommitted).isTrue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(service.complete(41L, "worker-1", result)).isTrue();
    }

    @Test
    void completionIsPublishedOnlyAfterTheTransactionCommits() {
        AsyncJobRepository repository = mock(AsyncJobRepository.class);
        AsyncJob job = runningJob();
        when(repository.findForUpdate(41L)).thenReturn(Optional.of(job));
        JobEventBroker events = mock(JobEventBroker.class);
        AsyncJobService service = service(repository, events);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.completeAtomically(41L, "worker-1", "{}", () -> { });

            verifyNoInteractions(events);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(events).publish(org.mockito.ArgumentMatchers.argThat(
                    view -> view.status() == JobStatus.COMPLETED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private AsyncJob runningJob() {
        AsyncJob job = new AsyncJob(JobKind.INGEST_DOCUMENT, 7L, 3L, "{}", "key", 3);
        ReflectionTestUtils.setField(job, "id", 41L);
        Instant now = Instant.now();
        assertThat(job.claim("worker-1", now, now.plusSeconds(30))).isTrue();
        return job;
    }

    private AsyncJobService service(AsyncJobRepository repository) {
        return service(repository, mock(JobEventBroker.class));
    }

    private AsyncJobService service(AsyncJobRepository repository, JobEventBroker events) {
        return new AsyncJobService(repository, mock(OutboxEventRepository.class), new ObjectMapper(),
                new SEForgeProperties(), events, mock(ApplicationEventPublisher.class));
    }
}
