package com.ustb.seforge.review.service;

import static com.ustb.seforge.review.service.StructuredReviewModels.AssignmentReviewResult;
import static com.ustb.seforge.review.service.StructuredReviewModels.CodeReviewResult;
import static com.ustb.seforge.review.service.StructuredReviewModels.DocumentReviewResult;

import com.ustb.seforge.review.api.ExternalSonarFinding;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ReviewResultValidator {
    public DocumentReviewResult document(DocumentReviewResult result) {
        if (result == null || blank(result.summary()) || result.dimensions() == null
                || result.dimensions().isEmpty()) {
            throw invalid("Document review is missing required sections");
        }
        Set<String> required = Set.of("completeness", "consistency", "verifiability", "clarity");
        Set<String> actual = new HashSet<>();
        result.dimensions().forEach(dimension -> {
            if (dimension != null && dimension.dimension() != null) {
                actual.add(dimension.dimension().trim().toLowerCase());
                bounded(dimension.score(), BigDecimal.ZERO, BigDecimal.valueOf(100),
                        "Document dimension score");
            }
        });
        if (!actual.equals(required) || actual.size() != result.dimensions().size()) {
            throw invalid("Document review must cover completeness, consistency, verifiability and clarity");
        }
        return result;
    }

    public AssignmentReviewResult assignment(AssignmentReviewResult result,
                                               Map<Long, BigDecimal> rubricMaximums) {
        if (result == null || blank(result.summary()) || result.rubricItems() == null) {
            throw invalid("Assignment review is incomplete");
        }
        Set<Long> seen = new HashSet<>();
        result.rubricItems().forEach(suggestion -> {
            if (suggestion == null || suggestion.rubricItemId() == null
                    || !rubricMaximums.containsKey(suggestion.rubricItemId())) {
                throw invalid("Assignment review references an unknown rubric item");
            }
            if (!seen.add(suggestion.rubricItemId())) {
                throw invalid("Assignment review contains duplicate rubric suggestions");
            }
            bounded(suggestion.suggestedScore(), BigDecimal.ZERO,
                    rubricMaximums.get(suggestion.rubricItemId()), "Suggested rubric score");
            if (blank(suggestion.feedback())) {
                throw invalid("Every rubric suggestion must include feedback");
            }
        });
        if (!seen.equals(rubricMaximums.keySet())) {
            throw invalid("Assignment review must evaluate every rubric item exactly once");
        }
        BigDecimal calculated = result.rubricItems().stream()
                .map(item -> item.suggestedScore() == null ? BigDecimal.ZERO : item.suggestedScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (result.totalSuggestedScore() == null
                || calculated.compareTo(result.totalSuggestedScore()) != 0) {
            throw invalid("Suggested total does not match rubric item suggestions");
        }
        return result;
    }

    public CodeReviewResult code(CodeReviewResult result, List<ExternalSonarFinding> findings) {
        if (result == null || blank(result.summary()) || result.explanations() == null) {
            throw invalid("Code review is incomplete");
        }
        Set<String> expected = findings.stream().map(ExternalSonarFinding::findingKey)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> actual = new HashSet<>();
        result.explanations().forEach(explanation -> {
            if (explanation == null || blank(explanation.findingKey())
                    || !expected.contains(explanation.findingKey())) {
                throw invalid("Code review invented or omitted the source finding identifier");
            }
            if (blank(explanation.explanation()) || blank(explanation.impact())
                    || blank(explanation.remediation())) {
                throw invalid("Every code finding must include explanation, impact and remediation");
            }
            if (!actual.add(explanation.findingKey())) {
                throw invalid("Code review contains duplicate finding explanations");
            }
        });
        if (!actual.equals(expected)) {
            throw invalid("Code review must explain every supplied static-analysis finding");
        }
        return result;
    }

    private void bounded(BigDecimal value, BigDecimal minimum, BigDecimal maximum, String label) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw invalid(label + " is outside its allowed range");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
