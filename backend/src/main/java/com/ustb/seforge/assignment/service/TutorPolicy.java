package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ustb.seforge.assignment.domain.TutorOperation;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TutorPolicy(
        Boolean allowFullSolutionBeforeSubmit,
        Boolean fullSolutionAfterSubmit,
        Boolean fullSolutionAfterDue,
        Boolean allowLateSubmission,
        List<TutorOperation> enabledOperations,
        Map<String, Instant> dueAtOverrides) {

    public static TutorPolicy defaults() {
        return new TutorPolicy(false, true, true, false, List.of(
                TutorOperation.HINT,
                TutorOperation.EXPLAIN,
                TutorOperation.CHECK_REASONING,
                TutorOperation.ANALYZE_ERROR,
                TutorOperation.EVALUATE_DRAFT,
                TutorOperation.FULL_SOLUTION), Map.of());
    }

    public boolean permits(TutorOperation operation, boolean hasValidSubmission, boolean afterDue) {
        List<TutorOperation> operations = enabledOperations == null ? defaults().enabledOperations : enabledOperations;
        if (!operations.contains(operation)) return false;
        if (operation != TutorOperation.FULL_SOLUTION) return true;
        return Boolean.TRUE.equals(allowFullSolutionBeforeSubmit)
                || (hasValidSubmission && isFullSolutionAfterSubmitEnabled())
                || (afterDue && isFullSolutionAfterDueEnabled());
    }

    public boolean isFullSolutionAfterSubmitEnabled() {
        return fullSolutionAfterSubmit == null || fullSolutionAfterSubmit;
    }

    public boolean isFullSolutionAfterDueEnabled() {
        return fullSolutionAfterDue == null || fullSolutionAfterDue;
    }

    public boolean isLateSubmissionAllowed() {
        return Boolean.TRUE.equals(allowLateSubmission);
    }

    public Instant effectiveDueAt(Long userId, Instant assignmentDueAt) {
        if (dueAtOverrides == null || userId == null) return assignmentDueAt;
        return dueAtOverrides.getOrDefault(userId.toString(), assignmentDueAt);
    }
}
