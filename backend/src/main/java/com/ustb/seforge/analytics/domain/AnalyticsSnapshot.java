package com.ustb.seforge.analytics.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "analytics_snapshot",
        indexes = @Index(name = "idx_analytics_snapshot_course",
                columnList = "course_id,metric_type,generated_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_analytics_snapshot_period",
                columnNames = {"course_id", "class_id", "metric_type", "period_end"}))
public class AnalyticsSnapshot extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "metric_type", nullable = false, length = 64)
    private String metricType;

    @Column(name = "source_cursor", nullable = false, length = 64)
    private String sourceCursor;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected AnalyticsSnapshot() {
    }

    public AnalyticsSnapshot(Long courseId, Long classId, String metricType, String sourceCursor,
                             Instant periodStart, Instant periodEnd, String payloadJson,
                             Instant generatedAt) {
        this.courseId = courseId;
        this.classId = classId;
        this.metricType = metricType;
        this.sourceCursor = sourceCursor;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.payloadJson = payloadJson;
        this.generatedAt = generatedAt;
    }

    public Long getCourseId() { return courseId; }
    public Long getClassId() { return classId; }
    public String getMetricType() { return metricType; }
    public String getSourceCursor() { return sourceCursor; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getGeneratedAt() { return generatedAt; }
}
