package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.service.TutorPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateAssignmentRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 100_000) String description,
        Long classId,
        Instant availableAt,
        Instant dueAt,
        @Min(1) @Max(100) Integer maxAttempts,
        TutorPolicy tutorPolicy) {
}
