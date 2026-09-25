package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.domain.Feedback;
import com.ustb.seforge.assignment.domain.FeedbackSource;
import com.ustb.seforge.assignment.domain.Grade;
import com.ustb.seforge.assignment.domain.GradeStatus;
import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import com.ustb.seforge.assignment.repository.FeedbackRepository;
import com.ustb.seforge.assignment.repository.GradeRepository;
import com.ustb.seforge.assignment.repository.RubricItemRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradeSuggestionService {
    private final SubmissionRepository submissions;
    private final GradeRepository grades;
    private final FeedbackRepository feedback;
    private final RubricItemRepository rubricItems;
    private final com.ustb.seforge.assignment.repository.RubricRepository rubrics;
    private final ObjectMapper objectMapper;

    public GradeSuggestionService(SubmissionRepository submissions, GradeRepository grades,
                                  FeedbackRepository feedback, RubricItemRepository rubricItems,
                                  com.ustb.seforge.assignment.repository.RubricRepository rubrics,
                                  ObjectMapper objectMapper) {
        this.submissions = submissions;
        this.grades = grades;
        this.feedback = feedback;
        this.rubricItems = rubricItems;
        this.rubrics = rubrics;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists an auditable AI suggestion only. This method never confirms or publishes a final grade.
     */
    @Transactional
    public Grade applySuggestion(Long submissionId, Long courseId, Long studentId, BigDecimal total,
                                 String model, String promptVersion,
                                 List<AiRubricSuggestion> itemSuggestions) {
        Submission submission = submissions.findForGrading(submissionId)
                .orElseThrow(() -> notFound("Submission not found"));
        if (!submission.getCourseId().equals(courseId) || !submission.getUserId().equals(studentId)) {
            throw notFound("Submission not found");
        }
        if (submission.getStatus() == SubmissionStatus.DRAFT) {
            throw new AppException(ErrorCode.CONFLICT, "Draft submissions cannot be graded");
        }
        if (total == null || total.signum() < 0 || total.stripTrailingZeros().scale() > 2) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Suggested score must be non-negative");
        }
        Grade grade = grades.findForUpdate(submissionId)
                .orElseGet(() -> new Grade(submissionId, courseId, studentId));
        if (grade.getStatus() == GradeStatus.CONFIRMED) {
            throw new AppException(ErrorCode.CONFLICT, "A confirmed grade cannot be replaced by AI");
        }
        grade.applyAiSuggestion(total, model, promptVersion);
        grades.save(grade);

        var rubric = rubrics.findByAssignmentId(submission.getAssignmentId()).orElse(null);
        if (rubric == null || itemSuggestions == null || itemSuggestions.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "A complete rubric suggestion is required");
        }
        if (rubric != null && total.compareTo(rubric.getTotalScore()) > 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Suggested score exceeds rubric total");
        }
        feedback.deleteAll(feedback.findAllByGradeIdAndSource(grade.getId(), FeedbackSource.AI));
        Long rubricId = rubric == null ? null : rubric.getId();
        BigDecimal itemTotal = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (AiRubricSuggestion item : itemSuggestions == null ? List.<AiRubricSuggestion>of() : itemSuggestions) {
            if (item == null || item.rubricItemId() == null || !seen.add(item.rubricItemId())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Rubric suggestions must be unique");
            }
            var rubricItem = rubricId == null ? null
                    : rubricItems.findByIdAndRubricId(item.rubricItemId(), rubricId).orElse(null);
            if (rubricItem == null) throw notFound("Rubric item not found");
            if (item.suggestedScore() == null || item.suggestedScore().signum() < 0 || item.suggestedScore().stripTrailingZeros().scale() > 2
                    || item.suggestedScore().compareTo(rubricItem.getMaxScore()) > 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid rubric score suggestion");
            }
            itemTotal = itemTotal.add(item.suggestedScore());
            feedback.save(Feedback.ai(grade.getId(), item.rubricItemId(), required(item.feedback()),
                    item.suggestedScore(), json(item.evidence()), json(item.issueCodes())));
        }
        Set<Long> expected = rubricItems.findAllByRubricIdOrderBySortOrderAscIdAsc(rubricId).stream()
                .map(value -> value.getId()).collect(java.util.stream.Collectors.toSet());
        if (!seen.equals(expected)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Every rubric item must appear exactly once");
        }
        if (itemTotal.compareTo(total) != 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Suggested total must equal the rubric item sum");
        }
        return grade;
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST, "Invalid AI grade suggestion");
        }
    }

    private String required(String value) {
        return value == null || value.isBlank() ? "No feedback supplied" : value;
    }

    private AppException notFound(String message) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
