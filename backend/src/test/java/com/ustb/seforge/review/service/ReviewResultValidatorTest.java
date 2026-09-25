package com.ustb.seforge.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ustb.seforge.review.api.ExternalSonarFinding;
import com.ustb.seforge.review.service.StructuredReviewModels.AssignmentReviewResult;
import com.ustb.seforge.review.service.StructuredReviewModels.CodeIssueExplanation;
import com.ustb.seforge.review.service.StructuredReviewModels.CodeReviewResult;
import com.ustb.seforge.review.service.StructuredReviewModels.DocumentDimension;
import com.ustb.seforge.review.service.StructuredReviewModels.DocumentReviewResult;
import com.ustb.seforge.review.service.StructuredReviewModels.RubricSuggestion;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewResultValidatorTest {
    private final ReviewResultValidator validator = new ReviewResultValidator();

    @Test
    void documentReviewMustCoverAllQualityDimensions() {
        DocumentReviewResult incomplete = new DocumentReviewResult("summary", List.of(
                dimension("completeness"), dimension("clarity")), List.of(), List.of());

        assertThatThrownBy(() -> validator.document(incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completeness, consistency, verifiability and clarity");
    }

    @Test
    void assignmentSuggestionsMustMatchRubricAndTotal() {
        AssignmentReviewResult valid = new AssignmentReviewResult("summary", new BigDecimal("8.00"),
                List.of(new RubricSuggestion(11L, new BigDecimal("8.00"),
                        List.of("answer line 1"), List.of(), "Clear reasoning")));

        assertThat(validator.assignment(valid, Map.of(11L, BigDecimal.TEN))).isSameAs(valid);
        assertThatThrownBy(() -> validator.assignment(valid, Map.of(12L, BigDecimal.TEN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown rubric item");
    }

    @Test
    void codeReviewCannotInventOrDropSonarFindings() {
        ExternalSonarFinding finding = new ExternalSonarFinding(
                "finding-1", "java:S2095", "BUG", "MAJOR", "App.java", 12, "Close resource");
        CodeReviewResult valid = new CodeReviewResult("summary", List.of(
                new CodeIssueExplanation("finding-1", "explanation", "impact", "fix")));
        assertThat(validator.code(valid, List.of(finding))).isSameAs(valid);

        CodeReviewResult invented = new CodeReviewResult("summary", List.of(
                new CodeIssueExplanation("invented", "explanation", "impact", "fix")));
        assertThatThrownBy(() -> validator.code(invented, List.of(finding)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invented");
    }

    private DocumentDimension dimension(String name) {
        return new DocumentDimension(name, BigDecimal.valueOf(80), List.of(), List.of());
    }

    @Test void rejectsMissingEvidenceMissingDocumentSectionsAndDuplicateCodeExplanations() {
        var dimensions = List.of(dimension("completeness"), dimension("consistency"), dimension("verifiability"), dimension("clarity"));
        assertThatThrownBy(() -> validator.document(new DocumentReviewResult("summary", dimensions, null, List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.assignment(new AssignmentReviewResult("summary", BigDecimal.ONE,
                List.of(new RubricSuggestion(1L, BigDecimal.ONE, List.of(), List.of(), "feedback"))), Map.of(1L, BigDecimal.TEN))).isInstanceOf(IllegalArgumentException.class);
        var finding = new ExternalSonarFinding("key", "rule", "BUG", "MAJOR", "file", 1, "message");
        var explanation = new CodeIssueExplanation("key", "explain", "impact", "fix");
        assertThatThrownBy(() -> validator.code(new CodeReviewResult("summary", List.of(explanation, explanation)), List.of(finding))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.code(new CodeReviewResult("summary", List.of()), List.of(finding))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsScoresThatWouldChangeWhenPersistedToDecimalTwoColumns() {
        assertThatThrownBy(() -> validator.assignment(new AssignmentReviewResult("summary", new BigDecimal("1.111"),
                List.of(new RubricSuggestion(1L, new BigDecimal("1.111"), List.of("evidence"), List.of(), "feedback"))),
                Map.of(1L, BigDecimal.TEN))).isInstanceOf(IllegalArgumentException.class);
    }
}
