package com.ustb.seforge.job.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.job.api.AsyncJobView;
import com.ustb.seforge.job.domain.AsyncJob;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.domain.JobStatus;
import com.ustb.seforge.job.domain.OutboxEvent;
import com.ustb.seforge.job.repository.AsyncJobRepository;
import com.ustb.seforge.job.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AsyncJobService {
    private final AsyncJobRepository jobs;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final SEForgeProperties properties;
    private final JobEventBroker events;
    private final ApplicationEventPublisher eventPublisher;

    public AsyncJobService(AsyncJobRepository jobs, OutboxEventRepository outbox, ObjectMapper objectMapper,
                           SEForgeProperties properties, JobEventBroker events,
                           ApplicationEventPublisher eventPublisher) {
        this.jobs = jobs;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.events = events;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AsyncJobView submit(JobKind kind, Long ownerUserId, Long courseId, Object payload,
                               String idempotencyKey) {
        String clientKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        String key = ownerUserId + ":" + kind + ":" + clientKey;
        Optional<AsyncJob> existing = jobs.findByOwnerUserIdAndKindAndIdempotencyKey(ownerUserId, kind, key);
        if (existing.isPresent()) return AsyncJobView.from(existing.get());
        AsyncJob job = jobs.save(new AsyncJob(kind, ownerUserId, courseId, json(payload), key,
                properties.getJobs().getMaxAttempts()));
        outbox.save(jobEvent(job, Instant.now()));
        return AsyncJobView.from(job);
    }

    @Transactional(readOnly = true)
    public AsyncJobView requireOwned(Long jobId, Long ownerUserId) {
        return jobs.findByIdAndOwnerUserId(jobId, ownerUserId).map(AsyncJobView::from)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found"));
    }

    @Transactional(readOnly = true)
    public AsyncJobView require(Long jobId) {
        return jobs.findById(jobId).map(AsyncJobView::from)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found"));
    }

    @Transactional(readOnly = true)
    public Optional<AsyncJobView> findForCourse(Long jobId, Long courseId) {
        return jobs.findByIdAndCourseId(jobId, courseId).map(AsyncJobView::from);
    }

    @Transactional(readOnly = true)
    public boolean isOwnedBy(Long jobId, Long userId) {
        return jobs.findByIdAndOwnerUserId(jobId, userId).isPresent();
    }

    @Transactional
    public AsyncJobView cancel(Long jobId, Long ownerUserId) {
        AsyncJob job = jobs.findForUpdate(jobId)
                .filter(candidate -> ownerUserId.equals(candidate.getOwnerUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found"));
        return requestCancellation(job);
    }

    @Transactional
    public AsyncJobView cancelForCourse(Long jobId, Long courseId) {
        AsyncJob job = jobs.findForUpdate(jobId)
                .filter(candidate -> courseId.equals(candidate.getCourseId()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found"));
        return requestCancellation(job);
    }

    private AsyncJobView requestCancellation(AsyncJob job) {
        boolean changed = job.requestCancel();
        AsyncJobView view = AsyncJobView.from(job);
        if (changed) {
            eventPublisher.publishEvent(new JobCancellationRequested(job.getId(), job.getKind(), job.getCourseId()));
        }
        publishAfterCommit(view);
        return view;
    }

    @Transactional
    public void markDispatched(Long jobId) {
        jobs.findForUpdate(jobId).ifPresent(job -> {
            job.markQueued();
            publishAfterCommit(AsyncJobView.from(job));
        });
    }

    @Transactional
    public Optional<JobSnapshot> claim(Long jobId, String workerId) {
        AsyncJob job = jobs.findForUpdate(jobId).orElse(null);
        if (job == null) return Optional.empty();
        Instant now = Instant.now();
        if (!job.claim(workerId, now, now.plus(properties.getJobs().getLeaseDuration()))) {
            if (job.getStatus() == JobStatus.CANCELLED) publishAfterCommit(AsyncJobView.from(job));
            return Optional.empty();
        }
        publishAfterCommit(AsyncJobView.from(job));
        return Optional.of(JobSnapshot.from(job));
    }

    @Transactional
    public boolean renewLease(Long jobId, String workerId) {
        AsyncJob job = jobs.findForUpdate(jobId).orElse(null);
        if (job == null) return false;
        Instant now = Instant.now();
        boolean renewed = job.renewLease(workerId, now, now.plus(properties.getJobs().getLeaseDuration()));
        if (!renewed && job.getStatus() == JobStatus.CANCELLED) publishAfterCommit(AsyncJobView.from(job));
        return renewed;
    }

    @Transactional
    public boolean complete(Long jobId, String workerId, String resultJson) {
        AsyncJob job = jobs.findForUpdate(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found"));
        if (job.getStatus() == JobStatus.COMPLETED) return true;
        boolean completed = job.complete(workerId, Instant.now(), normalizeJson(resultJson));
        if (completed) publishAfterCommit(AsyncJobView.from(job));
        return completed;
    }

    @Transactional(readOnly = true)
    public boolean isExecutionActive(Long jobId, String workerId) {
        Instant now = Instant.now();
        return jobs.findById(jobId).map(job -> job.canCommit(workerId, now)).orElse(false);
    }

    @Transactional
    public void completeAtomically(Long jobId, String workerId, String resultJson, Runnable domainCommit) {
        completeAtomically(jobId, workerId, () -> {
            domainCommit.run();
            return resultJson;
        });
    }

    @Transactional
    public String completeAtomically(Long jobId, String workerId, Supplier<String> domainCommit) {
        AsyncJob job = jobs.findForUpdate(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found"));
        Instant now = Instant.now();
        if (!job.canCommit(workerId, now)) {
            if (job.isCancelRequested()) job.complete(workerId, now, null);
            publishAfterCommit(AsyncJobView.from(job));
            throw new JobExecutionAbortedException("Job was cancelled or its lease was lost");
        }
        String resultJson = domainCommit.get();
        if (!job.complete(workerId, Instant.now(), normalizeJson(resultJson))) {
            throw new JobExecutionAbortedException("Job completion lease was lost");
        }
        publishAfterCommit(AsyncJobView.from(job));
        return resultJson;
    }

    @Transactional
    public void runIfActive(Long jobId, String workerId, Runnable action) {
        AsyncJob job = jobs.findForUpdate(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found"));
        if (!job.canCommit(workerId, Instant.now())) {
            throw new JobExecutionAbortedException("Job was cancelled or its lease was lost");
        }
        action.run();
    }

    @Transactional
    public boolean fail(Long jobId, String workerId, Throwable failure) {
        AsyncJob job = jobs.findForUpdate(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found"));
        long backoffSeconds = Math.min(60, 1L << Math.min(job.getAttempts(), 6));
        Instant retryAt = Instant.now().plusSeconds(backoffSeconds);
        boolean owned = job.fail(workerId, Instant.now(), failure.getMessage(), retryAt);
        if (!owned) return false;
        boolean retry = job.getStatus() == JobStatus.RETRY_WAIT;
        if (retry) outbox.save(jobEvent(job, retryAt));
        publishAfterCommit(AsyncJobView.from(job));
        return true;
    }

    /**
     * Reconciles durable database state with the Redis stream. It covers both
     * Redis data loss after an outbox event was marked published and worker
     * crashes after a job was claimed. Pessimistic row locks keep multiple API
     * replicas from creating competing recovery transitions.
     */
    @Transactional
    public int recoverStalled(Instant now) {
        Instant queuedBefore = now.minus(properties.getJobs().getQueuedStaleAfter());
        int batchSize = Math.max(1, Math.min(properties.getJobs().getRecoveryBatchSize(), 500));
        List<AsyncJob> recoverable = jobs.findRecoverable(now, queuedBefore,
                PageRequest.of(0, batchSize));
        int recovered = 0;
        for (AsyncJob job : recoverable) {
            Instant availableAt = now;
            boolean enqueue;
            if (job.getStatus() == JobStatus.RUNNING) {
                long backoffSeconds = Math.min(60, 1L << Math.min(job.getAttempts(), 6));
                availableAt = now.plusSeconds(backoffSeconds);
                enqueue = job.recoverExpiredLease(now, availableAt);
            } else {
                enqueue = job.recoverUndelivered(availableAt);
            }
            if (enqueue && !hasPendingDispatch(job.getId())) {
                outbox.save(jobEvent(job, availableAt));
                recovered++;
            }
            publishAfterCommit(AsyncJobView.from(job));
        }
        return recovered;
    }

    private boolean hasPendingDispatch(Long jobId) {
        return outbox.existsByAggregateTypeAndAggregateIdAndEventTypeAndStatus(
                "AsyncJob", jobId, "job.requested", com.ustb.seforge.job.domain.OutboxStatus.PENDING);
    }

    private void publishAfterCommit(AsyncJobView view) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    events.publish(view);
                }
            });
            return;
        }
        events.publish(view);
    }

    private OutboxEvent jobEvent(AsyncJob job, Instant availableAt) {
        return new OutboxEvent("AsyncJob", job.getId(), "job.requested",
                json(Map.of("jobId", job.getId(), "type", job.getKind().name())), availableAt);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST, "Payload cannot be serialized");
        }
    }

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) return "{}";
        try {
            objectMapper.readTree(value);
            return value;
        } catch (JsonProcessingException exception) {
            return json(Map.of("result", value));
        }
    }
}
