package com.ustb.seforge.assignment.api;

import java.math.BigDecimal;
import java.time.Instant;

public record GradeRecordView(
        Long id,
        Long courseId,
        Long assignmentId,
        String assignmentTitle,
        Long studentId,
        String studentName,
        BigDecimal score,
        BigDecimal maxScore,
        String status,
        String feedback,
        Instant gradedAt,
        BigDecimal aiSuggestedScore,
        String model,
        String promptVersion,
        String overrideReason) {
}
