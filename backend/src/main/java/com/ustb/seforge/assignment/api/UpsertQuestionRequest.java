package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.domain.QuestionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record UpsertQuestionRequest(
        @NotNull QuestionType type,
        @NotBlank @Size(max = 100_000) String prompt,
        List<@Size(max = 10_000) String> options,
        @Size(max = 100_000) String referenceAnswer,
        @NotNull @DecimalMin(value = "0.01") BigDecimal points,
        @Min(0) int orderIndex,
        Long knowledgePointId,
        Map<String, Object> config) {
}
