package com.ustb.seforge.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.ustb.seforge.job.domain.AsyncJob;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.domain.JobStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AsyncJobTest {
    @Test
    void duplicateDispatchCannotRevokeLeaseOrBypassRetryDelay() {
        AsyncJob job = new AsyncJob(JobKind.REVIEW_DOCUMENT, 7L, 3L, "{}", "duplicate", 3);
        Instant now = Instant.now().plusSeconds(1);
        assertThat(job.claim("worker", now, now.plusSeconds(30))).isTrue();
        job.markQueued();
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.renewLease("worker", now, now.plusSeconds(40))).isTrue();
        assertThat(job.claim("other", now, now.plusSeconds(30))).isFalse();
        job.fail("worker", now, "retry", now.plusSeconds(10));
        job.markQueued();
        assertThat(job.claim("other", now.plusSeconds(1), now.plusSeconds(30))).isFalse();
        assertThat(job.claim("other", now.plusSeconds(10), now.plusSeconds(40))).isTrue();
        assertThat(job.complete("other", now.plusSeconds(11), "{}")).isTrue();
        job.markQueued();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }
    @Test
    void retriesUntilMaxAttemptsThenMovesToDeadLetter() {
        AsyncJob job = new AsyncJob(JobKind.REVIEW_DOCUMENT, 7L, 3L, "{}", "key", 3);
        Instant now = Instant.now();

        assertThat(job.claim("worker", now, now.plusSeconds(30))).isTrue();
        assertThat(job.fail("worker", now, "first", now)).isTrue();
        assertThat(job.claim("worker", now.plusSeconds(1), now.plusSeconds(31))).isTrue();
        assertThat(job.fail("worker", now.plusSeconds(1), "second", now)).isTrue();
        assertThat(job.claim("worker", now.plusSeconds(2), now.plusSeconds(32))).isTrue();
        assertThat(job.fail("worker", now.plusSeconds(2), "third", now)).isTrue();

        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD_LETTER);
        assertThat(job.getAttempts()).isEqualTo(3);
    }

    @Test
    void cancellationBeforeClaimIsTerminal() {
        AsyncJob job = new AsyncJob(JobKind.INGEST_DOCUMENT, 7L, 3L, "{}", "key", 3);
        job.requestCancel();

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.claim("worker", Instant.now(), Instant.now().plusSeconds(30))).isFalse();
    }

    @Test
    void cancellationRequestedDuringExecutionWinsOverCompletionOrRetry() {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        AsyncJob completedWorker = new AsyncJob(JobKind.REVIEW_CODE, 7L, 3L, "{}", "cancel-1", 3);
        assertThat(completedWorker.claim("worker", now, now.plusSeconds(30))).isTrue();
        completedWorker.requestCancel();
        completedWorker.complete("worker", now.plusSeconds(1), "{}");
        assertThat(completedWorker.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(completedWorker.getLeaseOwner()).isNull();

        AsyncJob failedWorker = new AsyncJob(JobKind.REVIEW_CODE, 7L, 3L, "{}", "cancel-2", 3);
        assertThat(failedWorker.claim("worker", now, now.plusSeconds(30))).isTrue();
        failedWorker.requestCancel();
        assertThat(failedWorker.fail("worker", now.plusSeconds(1), "provider failed", now.plusSeconds(5))).isTrue();
        assertThat(failedWorker.getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void expiredLeaseIsRetriedAndEventuallyDeadLettered() {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        AsyncJob job = new AsyncJob(JobKind.REVIEW_DOCUMENT, 7L, 3L, "{}", "lease-key", 2);

        assertThat(job.claim("worker-1", now, now.plusSeconds(10))).isTrue();
        assertThat(job.recoverExpiredLease(now.plusSeconds(11), now.plusSeconds(13))).isTrue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
        assertThat(job.getLastError()).isEqualTo("Worker lease expired");
        assertThat(job.claim("worker-2", now.plusSeconds(13), now.plusSeconds(23))).isTrue();

        assertThat(job.recoverExpiredLease(now.plusSeconds(24), now.plusSeconds(28))).isFalse();
        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD_LETTER);
        assertThat(job.getLeaseOwner()).isNull();
    }

    @Test
    void staleQueuedDeliveryCanBeRecreatedOnlyOncePerTransition() {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        AsyncJob job = new AsyncJob(JobKind.INGEST_DOCUMENT, 7L, 3L, "{}", "queued-key", 3);
        job.markQueued();

        assertThat(job.recoverUndelivered(now)).isTrue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.recoverUndelivered(now.plusSeconds(1))).isFalse();
    }

    @Test
    void onlyCurrentLeaseOwnerCanRenewOrFinalize() {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        AsyncJob job = new AsyncJob(JobKind.REVIEW_CODE, 7L, 3L, "{}", "owner-key", 3);
        assertThat(job.claim("worker-1", now, now.plusSeconds(10))).isTrue();

        assertThat(job.renewLease("worker-2", now.plusSeconds(1), now.plusSeconds(20))).isFalse();
        assertThat(job.complete("worker-2", now.plusSeconds(1), "{}")).isFalse();
        assertThat(job.complete("worker-1", now.plusSeconds(11), "{}")).isFalse();
        assertThat(job.renewLease("worker-1", now.plusSeconds(2), now.plusSeconds(20))).isTrue();
        assertThat(job.complete("worker-1", now.plusSeconds(3), "{}")).isTrue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void cancellingATerminalJobIsANoOp() {
        Instant now = Instant.now();
        AsyncJob job = new AsyncJob(JobKind.REVIEW_DOCUMENT, 7L, 3L, "{}", "terminal", 3);
        assertThat(job.claim("worker", now, now.plusSeconds(30))).isTrue();
        assertThat(job.complete("worker", now.plusSeconds(1), "{}")).isTrue();

        assertThat(job.requestCancel()).isFalse();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.isCancelRequested()).isFalse();
    }
}
