package com.ustb.seforge.assignment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ConfirmGradeRequest(
        @NotNull @DecimalMin("0.0") BigDecimal score,
        @Size(max = 20_000) String feedback,
        @Size(max = 5_000) String reason,
        List<@Valid ConfirmRubricScoreRequest> rubricItems) {
}
