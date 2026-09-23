package com.ustb.seforge.assignment.service;

import com.ustb.seforge.assignment.api.ConfirmGradeRequest;
import com.ustb.seforge.assignment.api.ConfirmRubricScoreRequest;
import com.ustb.seforge.assignment.api.GradeRecordView;
import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.Feedback;
import com.ustb.seforge.assignment.domain.FeedbackSource;
import com.ustb.seforge.assignment.domain.Grade;
import com.ustb.seforge.assignment.domain.GradeStatus;
import com.ustb.seforge.assignment.domain.Rubric;
import com.ustb.seforge.assignment.domain.RubricItem;
import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import com.ustb.seforge.assignment.repository.AssignmentQuestionRepository;
import com.ustb.seforge.assignment.repository.AssignmentRepository;
import com.ustb.seforge.assignment.repository.FeedbackRepository;
import com.ustb.seforge.assignment.repository.GradeRepository;
import com.ustb.seforge.assignment.repository.RubricItemRepository;
import com.ustb.seforge.assignment.repository.RubricRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.identity.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradeService {
    private final GradeRepository grades;
    private final FeedbackRepository feedback;
    private final SubmissionRepository submissions;
    private final AssignmentRepository assignments;
    private final AssignmentQuestionRepository questions;
    private final RubricRepository rubrics;
    private final RubricItemRepository rubricItems;
    private final UserRepository users;
    private final CourseAccessService courseAccess;

    public GradeService(GradeRepository grades, FeedbackRepository feedback,
                        SubmissionRepository submissions, AssignmentRepository assignments,
                        AssignmentQuestionRepository questions, RubricRepository rubrics,
                        RubricItemRepository rubricItems, UserRepository users,
                        CourseAccessService courseAccess) {
        this.grades = grades;
        this.feedback = feedback;
        this.submissions = submissions;
        this.assignments = assignments;
        this.questions = questions;
        this.rubrics = rubrics;
        this.rubricItems = rubricItems;
        this.users = users;
        this.courseAccess = courseAccess;
    }

    @Transactional(readOnly = true)
    public PageResponse<GradeRecordView> list(Long courseId, Long userId, int page, int size) {
        if (courseId == null) return new PageResponse<>(List.of(), 0, Math.min(Math.max(1, size), 100), 0);
        courseAccess.requireMember(courseId, userId);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<Grade> result = isStaff(courseId, userId)
                ? grades.findAllByCourseIdOrderByUpdatedAtDesc(courseId, pageable)
                : grades.findAllByStudentIdAndCourseIdAndStatusOrderByUpdatedAtDesc(
                        userId, courseId, GradeStatus.CONFIRMED, pageable);
        return PageResponse.from(result.map(this::view));
    }

    @Transactional(readOnly = true)
    public GradeRecordView getForSubmission(Long submissionId, Long userId) {
        Submission submission = requireSubmission(submissionId);
        courseAccess.requireMember(submission.getCourseId(), userId);
        boolean staff = isStaff(submission.getCourseId(), userId);
        if (!staff && !submission.getUserId().equals(userId)) throw denied();
        Grade grade = grades.findBySubmissionId(submissionId)
                .orElseThrow(() -> notFound("Grade not found"));
        if (!staff && grade.getStatus() != GradeStatus.CONFIRMED) throw notFound("Grade not found");
        return view(grade);
    }

    @Transactional
    public GradeRecordView confirm(Long submissionId, Long graderId, ConfirmGradeRequest request) {
        Submission submission = requireSubmission(submissionId);
        courseAccess.requireTeacherOrAdmin(submission.getCourseId(), graderId);
        if (submission.getStatus() == SubmissionStatus.DRAFT) {
            throw new AppException(ErrorCode.CONFLICT, "Draft submissions cannot be graded");
        }
        BigDecimal maximum = maximumScore(submission.getAssignmentId());
        if (request.score().compareTo(maximum) > 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Final score exceeds assignment maximum");
        }
        Grade grade = grades.findBySubmissionId(submissionId)
                .orElseGet(() -> grades.save(new Grade(submissionId, submission.getCourseId(), submission.getUserId())));
        if (grade.getStatus() == GradeStatus.CONFIRMED) {
            throw new AppException(ErrorCode.CONFLICT, "Grade is already confirmed");
        }
        RubricConfirmationPlan confirmationPlan = validateRubricConfirmations(
                grade, submission.getAssignmentId(), request.rubricItems(), request.score());
        boolean totalOverride = grade.getAiSuggestedScore() != null
                && grade.getAiSuggestedScore().compareTo(request.score()) != 0;
        if ((totalOverride || confirmationPlan.overridesAiSuggestion())
                && (request.reason() == null || request.reason().isBlank())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "A reason is required when overriding an AI score suggestion");
        }

        applyRubricConfirmations(grade, graderId, confirmationPlan);
        if (request.feedback() != null && !request.feedback().isBlank()) {
            feedback.save(Feedback.teacher(grade.getId(), null, graderId, request.feedback(), request.score()));
        }
        grade.confirm(graderId, request.score(), request.reason(), Instant.now());
        if (submission.getStatus() == SubmissionStatus.SUBMITTED) submission.markGraded();
        return view(grade);
    }

    private RubricConfirmationPlan validateRubricConfirmations(
            Grade grade, Long assignmentId, List<ConfirmRubricScoreRequest> confirmations,
            BigDecimal requestedTotal) {
        Rubric rubric = rubrics.findByAssignmentId(assignmentId)
                .orElseThrow(() -> notFound("Rubric not found"));
        List<RubricItem> expectedItems = rubricItems
                .findAllByRubricIdOrderBySortOrderAscIdAsc(rubric.getId());
        if (expectedItems.isEmpty()) {
            throw new AppException(ErrorCode.CONFLICT, "Rubric has no items");
        }
        if (confirmations == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Every rubric item must be confirmed exactly once");
        }

        Map<Long, RubricItem> expectedById = new HashMap<>();
        expectedItems.forEach(item -> expectedById.put(item.getId(), item));
        Set<Long> confirmedIds = new HashSet<>();
        BigDecimal confirmedTotal = BigDecimal.ZERO;
        for (ConfirmRubricScoreRequest requested : confirmations) {
            if (requested == null || requested.rubricItemId() == null
                    || !confirmedIds.add(requested.rubricItemId())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Every rubric item must be confirmed exactly once");
            }
            RubricItem item = expectedById.get(requested.rubricItemId());
            if (item == null) {
                throw notFound("Rubric item not found");
            }
            if (requested.score() == null || requested.score().signum() < 0
                    || requested.score().compareTo(item.getMaxScore()) > 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Rubric score exceeds item maximum: " + item.getTitle());
            }
            confirmedTotal = confirmedTotal.add(requested.score());
        }
        if (!confirmedIds.equals(expectedById.keySet())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Every rubric item must be confirmed exactly once");
        }
        if (confirmedTotal.compareTo(requestedTotal) != 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Final score must equal the sum of confirmed rubric item scores");
        }

        List<Feedback> existing = feedback.findAllByGradeIdOrderByIdAsc(grade.getId());
        boolean overridesAiSuggestion = confirmations.stream().anyMatch(requested -> existing.stream()
                .filter(value -> value.getSource() == FeedbackSource.AI)
                .filter(value -> requested.rubricItemId().equals(value.getRubricItemId()))
                .map(Feedback::getSuggestedScore)
                .filter(score -> score != null)
                .anyMatch(score -> score.compareTo(requested.score()) != 0));
        return new RubricConfirmationPlan(List.copyOf(confirmations), existing, overridesAiSuggestion);
    }

    private void applyRubricConfirmations(Grade grade, Long graderId, RubricConfirmationPlan plan) {
        for (ConfirmRubricScoreRequest requested : plan.confirmations()) {
            Feedback row = plan.existingFeedback().stream()
                    .filter(value -> value.getSource() == FeedbackSource.TEACHER)
                    .filter(value -> requested.rubricItemId().equals(value.getRubricItemId()))
                    .findFirst().orElse(null);
            if (row == null) {
                feedback.save(Feedback.teacher(grade.getId(), requested.rubricItemId(), graderId,
                        requested.feedback() == null ? "Confirmed" : requested.feedback(), requested.score()));
            } else {
                row.confirm(graderId, requested.score(), requested.feedback());
            }
        }
    }

    private record RubricConfirmationPlan(
            List<ConfirmRubricScoreRequest> confirmations,
            List<Feedback> existingFeedback,
            boolean overridesAiSuggestion) {
    }

    private GradeRecordView view(Grade grade) {
        Submission submission = requireSubmission(grade.getSubmissionId());
        Assignment assignment = assignments.findById(submission.getAssignmentId())
                .orElseThrow(() -> notFound("Assignment not found"));
        String studentName = users.findById(grade.getStudentId()).map(value -> value.getUsername()).orElse(null);
        List<Feedback> feedbackRows = feedback.findAllByGradeIdOrderByIdAsc(grade.getId());
        String content = feedbackRows.stream()
                .filter(value -> value.getFinalScore() != null)
                .map(Feedback::getContent).filter(value -> value != null && !value.isBlank())
                .distinct().reduce((left, right) -> left + "\n" + right).orElse(null);
        boolean confirmed = grade.getStatus() == GradeStatus.CONFIRMED;
        return new GradeRecordView(grade.getId(), grade.getCourseId(), assignment.getId(), assignment.getTitle(),
                grade.getStudentId(), studentName,
                confirmed ? grade.getFinalScore() : grade.getAiSuggestedScore(), maximumScore(assignment.getId()),
                confirmed ? "FINAL" : "PENDING_CONFIRMATION", content,
                confirmed ? grade.getConfirmedAt() : grade.getUpdatedAt(), grade.getAiSuggestedScore(),
                grade.getModelName(), grade.getPromptVersion(), grade.getOverrideReason());
    }

    private BigDecimal maximumScore(Long assignmentId) {
        return rubrics.findByAssignmentId(assignmentId).map(Rubric::getTotalScore).orElseGet(() ->
                questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(assignmentId).stream()
                        .map(value -> value.getMaxScore()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private boolean isStaff(Long courseId, Long userId) {
        if (courseAccess.isAdmin(userId)) return true;
        return courseAccess.roleFor(courseId, userId)
                .map(role -> role == CourseMemberRole.TEACHER || role == CourseMemberRole.TA).orElse(false);
    }

    private Submission requireSubmission(Long submissionId) {
        return submissions.findById(submissionId).orElseThrow(() -> notFound("Submission not found"));
    }

    private AppException notFound(String message) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private AppException denied() {
        return new AppException(ErrorCode.ACCESS_DENIED, "You do not have access to this grade");
    }
}
