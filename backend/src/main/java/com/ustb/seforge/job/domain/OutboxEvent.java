package com.ustb.seforge.job.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "outbox_event", indexes = @Index(
        name = "idx_outbox_event_publish", columnList = "status,available_at,id"))
public class OutboxEvent extends BaseEntity {
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, Long aggregateId, String eventType,
                       String payloadJson, Instant availableAt) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.status = OutboxStatus.PENDING;
        this.availableAt = availableAt;
    }

    public void published() {
        status = OutboxStatus.PUBLISHED;
        publishedAt = Instant.now();
    }

    public void retryLater() {
        attempts++;
        long seconds = Math.min(60, 1L << Math.min(attempts, 6));
        availableAt = Instant.now().plus(Duration.ofSeconds(seconds));
    }

    public String getAggregateType() { return aggregateType; }
    public Long getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayloadJson() { return payloadJson; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
