package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.domain.AssignmentStatus;
import com.ustb.seforge.assignment.service.TutorPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AssignmentDetailsView(
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
        BigDecimal score,
        TutorPolicy tutorPolicy,
        List<AssignmentQuestionView> questions) {
}
