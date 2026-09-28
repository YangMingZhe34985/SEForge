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
    private final com.ustb.seforge.common.audit.AuditService audit;
    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;

    public GradeService(GradeRepository grades, FeedbackRepository feedback,
                        SubmissionRepository submissions, AssignmentRepository assignments,
                        AssignmentQuestionRepository questions, RubricRepository rubrics,
                        RubricItemRepository rubricItems, UserRepository users,
                        CourseAccessService courseAccess, com.ustb.seforge.common.audit.AuditService audit,
                        AssignmentService assignmentService, SubmissionService submissionService) {
        this.grades = grades;
        this.feedback = feedback;
        this.submissions = submissions;
        this.assignments = assignments;
        this.questions = questions;
        this.rubrics = rubrics;
        this.rubricItems = rubricItems;
        this.users = users;
        this.courseAccess = courseAccess;
        this.audit = audit;
        this.assignmentService = assignmentService;
        this.submissionService = submissionService;
    }

    @Transactional(readOnly = true)
    public PageResponse<GradeRecordView> list(Long courseId, Long userId, int page, int size) {
        if (courseId == null) return new PageResponse<>(List.of(), 0, Math.min(Math.max(1, size), 100), 0);
        courseAccess.requireMember(courseId, userId);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<Grade> result = isStaff(courseId, userId)
                ? grades.findAllByCourseIdOrderByUpdatedAtDesc(courseId, pageable)
                : grades.findAllByStudentIdAndCourseIdAndStatusOrderByUpdatedAtDesc(
                        userId, courseId, GradeStatus.PUBLISHED, pageable);
        return PageResponse.from(result.map(g -> view(g, isStaff(courseId,userId))));
    }

    @Transactional(readOnly = true)
    public GradeRecordView getForSubmission(Long submissionId, Long userId) {
        Submission submission = requireSubmission(submissionId);
        courseAccess.requireMember(submission.getCourseId(), userId);
        boolean staff = isStaff(submission.getCourseId(), userId);
        if (!staff && !submission.getUserId().equals(userId)) throw denied();
        Grade grade = grades.findBySubmissionId(submissionId)
                .orElseThrow(() -> notFound("Grade not found"));
        if (!staff && grade.getStatus() != GradeStatus.PUBLISHED) throw notFound("Grade not found");
        return view(grade, staff);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public GradeRecordView confirm(Long submissionId, Long graderId, ConfirmGradeRequest request) {
        Submission submission = submissions.findForGrading(submissionId)
                .orElseThrow(() -> notFound("Submission not found"));
        courseAccess.requireTeacherOrAdmin(submission.getCourseId(), graderId);
        if (submission.getStatus() == SubmissionStatus.DRAFT) {
            throw new AppException(ErrorCode.CONFLICT, "Draft submissions cannot be graded");
        }
        BigDecimal maximum = maximumScore(submission.getAssignmentId());
        if (request.score() == null || request.score().signum() < 0 || request.score().stripTrailingZeros().scale() > 2
                || request.score().compareTo(maximum) > 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Final score exceeds assignment maximum");
        }
        Grade grade = grades.findForUpdate(submissionId)
                .orElseGet(() -> grades.save(new Grade(submissionId, submission.getCourseId(), submission.getUserId())));
        if (grade.isFinal()) {
            throw new AppException(ErrorCode.CONFLICT, "Grade is already confirmed");
        }
        if (request.expectedAiTraceId() != null && !request.expectedAiTraceId().equals(grade.getAiTraceId())) {
            throw new AppException(ErrorCode.CONFLICT, "AI suggestion changed; reload the latest review before confirming");
        }
        RubricConfirmationPlan confirmationPlan = validateRubricConfirmations(
                grade, submission.getAssignmentId(), request.rubricItems(), request.score());
        // A partial RULE/AI total excludes MANUAL items; completing those is not an override.
        if (confirmationPlan.overridesAiSuggestion()
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
        audit.record(graderId, submission.getCourseId(), "GRADE_CONFIRMED", "GRADE", grade.getId(),
                com.ustb.seforge.common.audit.AuditService.SUCCEEDED);
        return view(grade);
    }

    private RubricConfirmationPlan validateRubricConfirmations(
            Grade grade, Long assignmentId, List<ConfirmRubricScoreRequest> confirmations,
            BigDecimal requestedTotal) {
        List<RubricItem> expectedItems = rubrics.findByAssignmentId(assignmentId)
                .map(r -> rubricItems.findAllByRubricIdOrderBySortOrderAscIdAsc(r.getId())).orElse(List.of());
        var targets = ScoringTargets.of(questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(assignmentId), expectedItems);
        if (targets.isEmpty()) {
            throw new AppException(ErrorCode.CONFLICT, "Rubric has no items");
        }
        if (confirmations == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Every rubric item must be confirmed exactly once");
        }

        Map<String, ScoringTargets.Target> expectedById = new HashMap<>();
        targets.forEach(item -> expectedById.put(item.key(), item));
        Set<String> confirmedIds = new HashSet<>();
        BigDecimal confirmedTotal = BigDecimal.ZERO;
        for (ConfirmRubricScoreRequest requested : confirmations) {
            if (requested == null || !confirmedIds.add(ScoringTargets.keyOf(requested.rubricItemId(), requested.questionId()))) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Every rubric item must be confirmed exactly once");
            }
            var item = expectedById.get(ScoringTargets.keyOf(requested.rubricItemId(), requested.questionId()));
            if (item == null) {
                throw notFound("Rubric item not found");
            }
            if (requested.score() == null || requested.score().signum() < 0 || requested.score().stripTrailingZeros().scale() > 2
                    || requested.score().compareTo(item.maximum()) > 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Score exceeds item maximum");
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
                .filter(value -> value.getSource() == FeedbackSource.AI || value.getSource() == FeedbackSource.RULE || value.getSource() == FeedbackSource.MANUAL)
                .filter(value -> java.util.Objects.equals(requested.rubricItemId(),value.getRubricItemId()) && java.util.Objects.equals(requested.questionId(),value.getQuestionId()))
                .map(Feedback::getSuggestedScore)
                .filter(score -> score != null)
                .anyMatch(score -> score.compareTo(requested.score()) != 0));
        return new RubricConfirmationPlan(List.copyOf(confirmations), existing, overridesAiSuggestion);
    }

    private void applyRubricConfirmations(Grade grade, Long graderId, RubricConfirmationPlan plan) {
        for (ConfirmRubricScoreRequest requested : plan.confirmations()) {
            Feedback row = plan.existingFeedback().stream()
                    .filter(value -> value.getSource() == FeedbackSource.TEACHER)
                    .filter(value -> java.util.Objects.equals(requested.rubricItemId(),value.getRubricItemId()) && java.util.Objects.equals(requested.questionId(),value.getQuestionId()))
                    .findFirst().orElse(null);
            if (row == null) {
                feedback.save(Feedback.teacher(grade.getId(), requested.rubricItemId(), graderId,
                        requested.feedback() == null ? "Confirmed" : requested.feedback(), requested.score()).forQuestion(requested.questionId()));
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
        return view(grade, true);
    }
    private GradeRecordView view(Grade grade, boolean staff) {
        Submission submission = requireSubmission(grade.getSubmissionId());
        Assignment assignment = assignments.findById(submission.getAssignmentId())
                .orElseThrow(() -> notFound("Assignment not found"));
        String studentName = users.findById(grade.getStudentId()).map(value -> value.getUsername()).orElse(null);
        List<Feedback> feedbackRows = feedback.findAllByGradeIdOrderByIdAsc(grade.getId());
        String content = feedbackRows.stream()
                .filter(value -> value.getFinalScore() != null)
                .map(Feedback::getContent).filter(value -> value != null && !value.isBlank())
                .distinct().reduce((left, right) -> left + "\n" + right).orElse(null);
        boolean confirmed = grade.isFinal();
        return new GradeRecordView(grade.getId(), grade.getCourseId(), assignment.getId(), assignment.getTitle(),
                grade.getStudentId(), studentName,
                confirmed ? grade.getFinalScore() : null, maximumScore(assignment.getId()),
                grade.getStatus().name(), content,
                grade.getUpdatedAt(), staff ? grade.getAiSuggestedScore() : null,
                staff ? grade.getModelName() : null, staff ? grade.getPromptVersion() : null, staff ? grade.getOverrideReason() : null, grade.getGraderId(),
                staff ? grade.getAiTraceId() : null, staff ? grade.getRuleSuggestedScore() : null,
                feedbackRows.stream().filter(row -> staff || row.getSource() == FeedbackSource.TEACHER).map(row -> new GradeRecordView.Item(
                        row.getRubricItemId(), row.getSource() == FeedbackSource.AI ? "AI_ASSISTED" : row.getSource().name(), row.getSuggestedScore(),
                        row.getFinalScore(), row.getContent(), row.getAuthorId(), row.getQuestionId())).toList(),
                submission.getId(), staff ? preliminaryTotal(grade, feedbackRows) : null, grade.getPublishedAt(), grade.getPublishedBy());
    }

    private BigDecimal preliminaryTotal(Grade grade, List<Feedback> rows) {
        var manual = rows.stream().filter(f -> f.getSource() == FeedbackSource.MANUAL).toList();
        if (manual.isEmpty()) return grade.getSuggestedScore();
        Map<String,BigDecimal> latest = new HashMap<>();
        manual.forEach(f -> latest.put(ScoringTargets.keyOf(f.getRubricItemId(),f.getQuestionId()),f.getSuggestedScore()));
        return latest.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
    }

    public record GradingDetail(GradeRecordView grade, com.ustb.seforge.assignment.api.AssignmentDetailsView assignment,
                                com.ustb.seforge.assignment.api.SubmissionView submission, List<ScoringTargets.Target> targets) {}
    @Transactional(readOnly=true)
    public GradingDetail detail(Long submissionId, Long actor) {
        Submission s = requireSubmission(submissionId);
        courseAccess.requireTeachingStaff(s.getCourseId(),actor);
        if(s.getStatus()==SubmissionStatus.DRAFT) throw notFound("Submitted attempt not found");
        Grade g=grades.findBySubmissionId(submissionId).orElseThrow(()->notFound("Grade not found"));
        var items=rubrics.findByAssignmentId(s.getAssignmentId()).map(r->rubricItems.findAllByRubricIdOrderBySortOrderAscIdAsc(r.getId())).orElse(List.of());
        return new GradingDetail(view(g),assignmentService.get(s.getAssignmentId(),actor),submissionService.view(s),
                ScoringTargets.of(questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(s.getAssignmentId()),items));
    }

    /** Append-only preliminary manual assessment; never writes final_score or marks submission graded. */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public GradeRecordView manual(Long submissionId, Long actor, ConfirmGradeRequest request) {
        Submission s=submissions.findForGrading(submissionId).orElseThrow(()->notFound("Submission not found"));
        courseAccess.requireTeachingStaff(s.getCourseId(),actor);
        if(s.getStatus()==SubmissionStatus.DRAFT) throw new AppException(ErrorCode.CONFLICT,"Draft cannot be reviewed");
        Grade g=grades.findForUpdate(submissionId).orElseGet(()->grades.save(new Grade(submissionId,s.getCourseId(),s.getUserId())));
        if(g.isFinal()) throw new AppException(ErrorCode.CONFLICT,"Final grade cannot be reviewed again");
        if(request.score()==null) throw QuestionContent.invalid("Manual total is required");
        var plan=validateRubricConfirmations(g,s.getAssignmentId(),request.rubricItems(),request.score());
        if(plan.overridesAiSuggestion() && (request.reason()==null||request.reason().isBlank())) throw QuestionContent.invalid("覆盖 RULE / AI / 人工初评必须填写修改理由");
        for(var item:plan.confirmations()) {
            String note=(item.feedback()==null?"":item.feedback()) + (request.feedback()==null?"":"\n"+request.feedback())
                    + (request.reason()==null?"":"\n修改理由："+request.reason());
            feedback.save(new Feedback(g.getId(),item.rubricItemId(),actor,FeedbackSource.MANUAL,note,item.score(),null,null).forQuestion(item.questionId()));
        }
        g.manuallyReviewed();
        audit.record(actor,s.getCourseId(),"GRADE_MANUAL_REVIEWED","GRADE",g.getId(),com.ustb.seforge.common.audit.AuditService.SUCCEEDED);
        return view(g);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public GradeRecordView publish(Long submissionId, Long actor) {
        Submission s=submissions.findForGrading(submissionId).orElseThrow(()->notFound("Submission not found"));
        courseAccess.requireTeacherOrAdmin(s.getCourseId(),actor);
        Grade g=grades.findForUpdate(submissionId).orElseThrow(()->notFound("Grade not found"));
        publishLocked(g,actor);
        return view(g);
    }
    private void publishLocked(Grade g,Long actor) {
        if(g.getStatus()==GradeStatus.PUBLISHED) return; // idempotent, no duplicate audit
        if(g.getStatus()!=GradeStatus.CONFIRMED) throw new AppException(ErrorCode.CONFLICT,"Only CONFIRMED grades can be published");
        g.publish(actor,Instant.now());
        audit.record(actor,g.getCourseId(),"GRADE_PUBLISHED","GRADE",g.getId(),com.ustb.seforge.common.audit.AuditService.SUCCEEDED);
    }
    public record PublicationPreview(long confirmed, long unconfirmed, long publishable, long published) {}
    @Transactional(readOnly=true)
    public PublicationPreview publicationPreview(Long assignmentId,Long actor) {
        var a=assignments.findById(assignmentId).orElseThrow(()->notFound("Assignment not found"));
        courseAccess.requireTeacherOrAdmin(a.getCourseId(),actor);
        var attempts=latestAttempts(assignmentId);
        long ready=0,published=0;
        for(var s:attempts) {var g=grades.findBySubmissionId(s.getId()).orElse(null);if(g!=null&&g.getStatus()==GradeStatus.CONFIRMED)ready++;else if(g!=null&&g.getStatus()==GradeStatus.PUBLISHED)published++;}
        return new PublicationPreview(ready+published,attempts.size()-ready-published,ready,published);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public PublicationPreview publishAssignment(Long assignmentId,Long actor) {
        var a=assignments.findByIdForUpdate(assignmentId).orElseThrow(()->notFound("Assignment not found"));
        courseAccess.requireTeacherOrAdmin(a.getCourseId(),actor);
        for(var s:latestAttempts(assignmentId).stream().sorted(java.util.Comparator.comparing(Submission::getId)).toList()) {
            submissions.findForGrading(s.getId()).orElseThrow();
            var g=grades.findForUpdate(s.getId()).orElse(null);
            if(g!=null&&g.getStatus()==GradeStatus.CONFIRMED)publishLocked(g,actor);
        }
        return publicationPreview(assignmentId,actor);
    }
    private List<Submission> latestAttempts(Long assignmentId) {
        Map<Long,Submission> latest=new HashMap<>();
        submissions.findAllByAssignmentIdOrderBySubmittedAtDesc(assignmentId).stream().filter(s->s.getStatus()!=SubmissionStatus.DRAFT)
                .forEach(s->latest.merge(s.getUserId(),s,(a,b)->a.getAttemptNo()>b.getAttemptNo()?a:b));
        return List.copyOf(latest.values());
    }

    private BigDecimal maximumScore(Long assignmentId) {
        return questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(assignmentId).stream()
                .map(value -> value.getMaxScore()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isStaff(Long courseId, Long userId) {
        return courseAccess.isTeachingStaff(courseId, userId);
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
