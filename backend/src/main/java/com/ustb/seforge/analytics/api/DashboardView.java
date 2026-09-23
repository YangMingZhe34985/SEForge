package com.ustb.seforge.analytics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardView(
        Long courseId,
        Long classId,
        Instant generatedAt,
        Overview overview,
        List<GradeBucket> gradeDistribution,
        List<KnowledgePointMetric> knowledgePoints,
        List<FrequentQuestion> frequentQuestions,
        TutorMetrics tutor,
        QaFeedbackMetrics qaFeedback,
        ErrorMetrics errors) {

    public record Overview(
            long students,
            long assignments,
            long expectedSubmissions,
            long completedSubmissions,
            BigDecimal completionRate,
            BigDecimal averageFinalScore) {
    }

    public record GradeBucket(String bucket, long count) {
    }

    public record KnowledgePointMetric(
            Long knowledgePointId,
            String title,
            BigDecimal scoreRate,
            long evaluatedItems,
            boolean weak) {
    }

    public record FrequentQuestion(String excerpt, long count) {
    }

    public record TutorMetrics(long total, long failed, Map<String, Long> byOperation) {
    }

    public record QaFeedbackMetrics(long helpful, long notHelpful, BigDecimal helpfulRate) {
    }

    public record ErrorMetrics(long tutorFailures, long reviewFailures) {
    }
}
