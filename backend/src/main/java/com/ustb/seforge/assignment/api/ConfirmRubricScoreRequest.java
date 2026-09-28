package com.ustb.seforge.assignment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ConfirmRubricScoreRequest(
        Long rubricItemId,
        @NotNull @DecimalMin("0.0") BigDecimal score,
        @Size(max = 20_000) String feedback,
        Long questionId) {
    public ConfirmRubricScoreRequest(Long rubricItemId, BigDecimal score, String feedback) {
        this(rubricItemId, score, feedback, null);
    }
}
