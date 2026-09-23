package com.ustb.seforge.review.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.review.domain.ReviewReport;
import com.ustb.seforge.review.domain.ReviewReportStatus;
import java.time.Instant;

public record ReviewReportView(
        Long id,
        Long reviewJobId,
        Long courseId,
        ReviewReportStatus status,
        String summary,
        JsonNode result,
        String model,
        String promptVersion,
        Instant generatedAt) {
    public static ReviewReportView from(ReviewReport report, ObjectMapper objectMapper) {
        try {
            return new ReviewReportView(report.getId(), report.getReviewJobId(), report.getCourseId(),
                    report.getStatus(), report.getSummary(),
                    objectMapper.readTree(report.getStructuredResultJson()), report.getModelName(),
                    report.getPromptVersion(), report.getGeneratedAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored review report is not valid JSON", exception);
        }
    }
}
