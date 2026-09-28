package com.ustb.seforge.assignment.service;

import java.math.BigDecimal;
import java.util.List;

public record AiRubricSuggestion(
        Long rubricItemId,
        BigDecimal suggestedScore,
        String feedback,
        List<String> evidence,
        List<String> issueCodes,
        Long questionId) {
    public AiRubricSuggestion(Long rubricItemId, BigDecimal suggestedScore, String feedback, List<String> evidence, List<String> issueCodes) {
        this(rubricItemId, suggestedScore, feedback, evidence, issueCodes, null);
    }
    public String key() { return ScoringTargets.keyOf(rubricItemId, questionId); }
}
