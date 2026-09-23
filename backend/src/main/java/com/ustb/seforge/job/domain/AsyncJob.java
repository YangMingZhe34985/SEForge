package com.ustb.seforge.job.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "async_job", indexes = {
        @Index(name = "idx_async_job_course", columnList = "course_id,job_type,status"),
        @Index(name = "idx_async_job_claim", columnList = "status,available_at,lease_expires_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_async_job_idempotency", columnNames = "idempotency_key"))
public class AsyncJob extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 64)
    private JobKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private JobStatus status;

    @Column(name = "requested_by")
    private Long ownerUserId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(name = "result", columnDefinition = "json")
    private String resultJson;

    @Column(name = "idempotency_key", nullable = false, length = 190)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "available_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "error_message", columnDefinition = "text")
    private String lastError;

    protected AsyncJob() {
    }

    public AsyncJob(JobKind kind, Long ownerUserId, Long courseId, String payloadJson,
                    String idempotencyKey, int maxAttempts) {
        this.kind = kind;
        this.status = JobStatus.PENDING;
        this.ownerUserId = ownerUserId;
        this.courseId = courseId;
        this.payloadJson = payloadJson;
        this.idempotencyKey = idempotencyKey;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = Instant.now();
    }

    public void markQueued() {
        if (!status.isTerminal() && !cancelRequested) {
            status = JobStatus.QUEUED;
        }
    }

    public boolean claim(String workerId, Instant now, Instant leaseUntil) {
        if (cancelRequested) {
            status = JobStatus.CANCELLED;
            clearLease();
            return false;
        }
        boolean claimable = status == JobStatus.PENDING || status == JobStatus.QUEUED
                || (status == JobStatus.RETRY_WAIT && !now.isBefore(nextAttemptAt));
        if (!claimable || (leaseExpiresAt != null && leaseExpiresAt.isAfter(now))) {
            return false;
        }
        status = JobStatus.RUNNING;
        attempts++;
        leaseOwner = workerId;
        leaseExpiresAt = leaseUntil;
        lastError = null;
        return true;
    }

    public boolean renewLease(String workerId, Instant now, Instant leaseUntil) {
        if (!ownedLeaseIsActive(workerId, now)) return false;
        if (cancelRequested) {
            status = JobStatus.CANCELLED;
            clearLease();
            return false;
        }
        leaseExpiresAt = leaseUntil;
        return true;
    }

    public boolean complete(String workerId, Instant now, String resultJson) {
        if (!ownedLeaseIsActive(workerId, now)) return false;
        if (cancelRequested) {
            status = JobStatus.CANCELLED;
            clearLease();
            return true;
        }
        status = JobStatus.COMPLETED;
        this.resultJson = resultJson;
        clearLease();
        return true;
    }

    public boolean fail(String workerId, Instant now, String message, Instant retryAt) {
        if (!ownedLeaseIsActive(workerId, now)) return false;
        transitionFailure(message, retryAt);
        return true;
    }

    private boolean transitionFailure(String message, Instant retryAt) {
        if (cancelRequested) {
            status = JobStatus.CANCELLED;
            clearLease();
            return false;
        }
        lastError = message == null ? "Job failed" : message.substring(0, Math.min(message.length(), 2000));
        clearLease();
        if (attempts >= maxAttempts) {
            status = JobStatus.DEAD_LETTER;
            return false;
        }
        status = JobStatus.RETRY_WAIT;
        nextAttemptAt = retryAt;
        return true;
    }

    /**
     * Moves a Redis-dispatched job back to the database outbox when no worker
     * claimed it within the configured delivery window. The transition makes
     * recovery idempotent: another recovery pass will no longer select it
     * until the replacement outbox event has been published.
     */
    public boolean recoverUndelivered(Instant availableAt) {
        if (status != JobStatus.QUEUED || cancelRequested) return false;
        status = JobStatus.PENDING;
        nextAttemptAt = availableAt;
        leaseOwner = null;
        leaseExpiresAt = null;
        return true;
    }

    /**
     * Treats an expired worker lease as a failed attempt. The attempt counter
     * was incremented on claim, so the normal retry/dead-letter policy applies.
     */
    public boolean recoverExpiredLease(Instant now, Instant retryAt) {
        if (status != JobStatus.RUNNING || leaseExpiresAt == null || leaseExpiresAt.isAfter(now)) {
            return false;
        }
        if (cancelRequested) {
            status = JobStatus.CANCELLED;
            clearLease();
            return false;
        }
        return transitionFailure("Worker lease expired", retryAt);
    }

    public boolean requestCancel() {
        if (status.isTerminal()) return false;
        cancelRequested = true;
        if (status != JobStatus.RUNNING) {
            status = JobStatus.CANCELLED;
            clearLease();
        }
        return true;
    }

    public boolean canCommit(String workerId, Instant now) {
        return !cancelRequested && ownedLeaseIsActive(workerId, now);
    }

    private void clearLease() {
        leaseOwner = null;
        leaseExpiresAt = null;
    }

    private boolean ownedLeaseIsActive(String workerId, Instant now) {
        return status == JobStatus.RUNNING
                && Objects.equals(leaseOwner, workerId)
                && leaseExpiresAt != null
                && leaseExpiresAt.isAfter(now);
    }

    public JobKind getKind() { return kind; }
    public JobStatus getStatus() { return status; }
    public Long getOwnerUserId() { return ownerUserId; }
    public Long getCourseId() { return courseId; }
    public String getPayloadJson() { return payloadJson; }
    public String getResultJson() { return resultJson; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public boolean isCancelRequested() { return cancelRequested; }
    public String getLastError() { return lastError; }
}
