package com.ustb.seforge.common.audit;

import java.time.Instant;

public record AuditLogView(
        Long id,
        Long actorId,
        String actorUsername,
        Long courseId,
        String action,
        String targetType,
        Long targetId,
        String outcome,
        String traceId,
        Instant occurredAt) {
}
