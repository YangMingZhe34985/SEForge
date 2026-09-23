package com.ustb.seforge.review.service;

import java.math.BigDecimal;
import java.util.List;

public final class StructuredReviewModels {
    private StructuredReviewModels() {
    }

    public record ReviewIssue(
            String code,
            String severity,
            String category,
            String message,
            String evidence,
            String recommendation) {
    }

    public record DocumentDimension(
            String dimension,
            BigDecimal score,
            List<String> findings,
            List<String> suggestions) {
    }

    public record DocumentReviewResult(
            String summary,
            List<DocumentDimension> dimensions,
            List<ReviewIssue> issues,
            List<String> recommendations) {
    }

    public record RubricSuggestion(
            Long rubricItemId,
            BigDecimal suggestedScore,
            List<String> evidence,
            List<String> issues,
            String feedback) {
    }

    public record AssignmentReviewResult(
            String summary,
            BigDecimal totalSuggestedScore,
            List<RubricSuggestion> rubricItems) {
    }

    public record CodeIssueExplanation(
            String findingKey,
            String explanation,
            String impact,
            String remediation) {
    }

    public record CodeReviewResult(
            String summary,
            List<CodeIssueExplanation> explanations) {
    }
}
