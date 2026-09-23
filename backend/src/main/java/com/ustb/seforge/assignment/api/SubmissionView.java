package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.domain.SubmissionStatus;
import java.time.Instant;
import java.util.List;

public record SubmissionView(
        Long id,
        Long assignmentId,
        SubmissionStatus status,
        List<SubmissionAnswerView> answers,
        int attemptNumber,
        Instant updatedAt,
        Instant submittedAt,
        boolean late) {
}
