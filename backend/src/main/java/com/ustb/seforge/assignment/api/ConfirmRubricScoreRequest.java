package com.ustb.seforge.assignment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ConfirmRubricScoreRequest(
        @NotNull Long rubricItemId,
        @NotNull @DecimalMin("0.0") BigDecimal score,
        @Size(max = 20_000) String feedback) {
}
