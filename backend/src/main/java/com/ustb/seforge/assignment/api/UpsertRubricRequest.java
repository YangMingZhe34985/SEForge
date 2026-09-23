package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.domain.RubricStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpsertRubricRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull @DecimalMin("0.01") BigDecimal totalScore,
        RubricStatus status) {
}
