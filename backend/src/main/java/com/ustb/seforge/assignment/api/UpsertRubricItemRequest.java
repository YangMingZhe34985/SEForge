package com.ustb.seforge.assignment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

public record UpsertRubricItemRequest(
        Long questionId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 10_000) String description,
        @NotNull @DecimalMin("0.01") BigDecimal maxScore,
        Map<String, Object> criteria,
        @Min(0) int orderIndex) {
}
