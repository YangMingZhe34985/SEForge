package com.ustb.seforge.analytics.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.analytics.domain.AnalyticsSnapshot;
import java.time.Instant;

public record AnalyticsSnapshotView(
        Long id,
        Long courseId,
        Long classId,
        String metricType,
        String sourceCursor,
        Instant periodStart,
        Instant periodEnd,
        Instant generatedAt,
        JsonNode payload) {
    public static AnalyticsSnapshotView from(AnalyticsSnapshot snapshot, ObjectMapper objectMapper) {
        try {
            return new AnalyticsSnapshotView(snapshot.getId(), snapshot.getCourseId(),
                    snapshot.getClassId(), snapshot.getMetricType(), snapshot.getSourceCursor(),
                    snapshot.getPeriodStart(), snapshot.getPeriodEnd(), snapshot.getGeneratedAt(),
                    objectMapper.readTree(snapshot.getPayloadJson()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored analytics snapshot is invalid", exception);
        }
    }
}
