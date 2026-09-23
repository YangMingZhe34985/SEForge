package com.ustb.seforge.review.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ExternalSonarFinding(
        @NotBlank @Size(max = 100) String findingKey,
        @NotBlank @Size(max = 150) String rule,
        @NotBlank @Size(max = 32) String type,
        @NotBlank @Size(max = 32) String severity,
        @NotBlank @Size(max = 500) String component,
        @PositiveOrZero Integer line,
        @NotBlank @Size(max = 2000) String message) {
}
