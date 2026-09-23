package com.ustb.seforge.common.audit;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "target_type", length = 80)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(nullable = false, length = 24)
    private String outcome;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditLog() {
    }

    public AuditLog(
            Long actorId, Long courseId, String action, String targetType,
            Long targetId, String outcome, String traceId) {
        this.actorId = actorId;
        this.courseId = courseId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.traceId = traceId;
        this.occurredAt = Instant.now();
    }
}
