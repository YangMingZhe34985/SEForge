package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.domain.AssignmentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AssignmentSummaryView(
        Long id,
        Long courseId,
        Long classId,
        String title,
        String description,
        AssignmentStatus status,
        Instant availableAt,
        Instant dueAt,
        int maxAttempts,
        boolean submitted,
        BigDecimal score) {
}
