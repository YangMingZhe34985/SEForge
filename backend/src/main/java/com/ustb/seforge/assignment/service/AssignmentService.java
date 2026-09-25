package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.api.AssignmentDetailsView;
import com.ustb.seforge.assignment.api.AssignmentQuestionView;
import com.ustb.seforge.assignment.api.AssignmentSummaryView;
import com.ustb.seforge.assignment.api.CreateAssignmentRequest;
import com.ustb.seforge.assignment.api.UpdateAssignmentRequest;
import com.ustb.seforge.assignment.api.UpsertQuestionRequest;
import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.assignment.domain.AssignmentStatus;
import com.ustb.seforge.assignment.domain.Grade;
import com.ustb.seforge.assignment.domain.GradeStatus;
import com.ustb.seforge.assignment.domain.QuestionType;
import com.ustb.seforge.assignment.domain.Rubric;
import com.ustb.seforge.assignment.domain.RubricItem;
import com.ustb.seforge.assignment.domain.RubricStatus;
import com.ustb.seforge.assignment.domain.Submission;
import com.ustb.seforge.assignment.domain.SubmissionStatus;
import com.ustb.seforge.assignment.repository.AssignmentQuestionRepository;
import com.ustb.seforge.assignment.repository.AssignmentRepository;
import com.ustb.seforge.assignment.repository.GradeRepository;
import com.ustb.seforge.assignment.repository.RubricItemRepository;
import com.ustb.seforge.assignment.repository.RubricRepository;
import com.ustb.seforge.assignment.repository.SubmissionRepository;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.domain.CourseMember;
import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.domain.CourseMemberStatus;
import com.ustb.seforge.course.repository.CourseClassRepository;
import com.ustb.seforge.course.repository.CourseMemberRepository;
import com.ustb.seforge.course.repository.KnowledgePointRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {
    private static final EnumSet<AssignmentStatus> STUDENT_STATUSES =
            EnumSet.of(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED);

    private final AssignmentRepository assignments;
    private final AssignmentQuestionRepository questions;
    private final SubmissionRepository submissions;
    private final GradeRepository grades;
    private final RubricRepository rubrics;
    private final RubricItemRepository rubricItems;
    private final CourseMemberRepository members;
    private final CourseClassRepository classes;
    private final KnowledgePointRepository knowledgePoints;
    private final CourseAccessService courseAccess;
    private final AssignmentAuthorizationService authorization;
    private final TutorPolicyCodec policies;
    private final ObjectMapper objectMapper;

    public AssignmentService(AssignmentRepository assignments, AssignmentQuestionRepository questions,
                             SubmissionRepository submissions, GradeRepository grades,
                             RubricRepository rubrics, RubricItemRepository rubricItems,
                             CourseMemberRepository members, CourseClassRepository classes,
                             KnowledgePointRepository knowledgePoints,
                             CourseAccessService courseAccess, AssignmentAuthorizationService authorization,
                             TutorPolicyCodec policies, ObjectMapper objectMapper) {
        this.assignments = assignments;
        this.questions = questions;
        this.submissions = submissions;
        this.grades = grades;
        this.rubrics = rubrics;
        this.rubricItems = rubricItems;
        this.members = members;
        this.classes = classes;
        this.knowledgePoints = knowledgePoints;
        this.courseAccess = courseAccess;
        this.authorization = authorization;
        this.policies = policies;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentSummaryView> list(Long courseId, Long userId, int page, int size) {
        courseAccess.requireMember(courseId, userId);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        boolean staff = isStaff(courseId, userId);
        Page<Assignment> result;
        if (staff) {
            result = assignments.findAllByCourseIdOrderByCreatedAtDesc(courseId, pageable);
        } else {
            CourseMember member = members.findByCourseIdAndUserIdAndStatus(courseId, userId, CourseMemberStatus.ACTIVE)
                    .orElseThrow(() -> denied("You do not have access to this course"));
            result = assignments.findVisibleForStudent(courseId, member.getClassId(), STUDENT_STATUSES, pageable);
        }
        return PageResponse.from(result.map(value -> summary(value, userId)));
    }

    @Transactional(readOnly = true)
    public AssignmentDetailsView get(Long assignmentId, Long userId) {
        Assignment assignment = require(assignmentId);
        authorization.requireVisible(assignment, userId);
        List<AssignmentQuestionView> views = questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(assignmentId)
                .stream().map(this::questionView).toList();
        AssignmentSummaryView summary = summary(assignment, userId);
        TutorPolicy tutorPolicy = policies.read(assignment.getTutorPolicyJson());
        if (!isStaff(assignment.getCourseId(), userId)) {
            tutorPolicy = new TutorPolicy(tutorPolicy.allowFullSolutionBeforeSubmit(),
                    tutorPolicy.fullSolutionAfterSubmit(), tutorPolicy.fullSolutionAfterDue(),
                    tutorPolicy.allowLateSubmission(), tutorPolicy.enabledOperations(), Map.of());
        }
        return new AssignmentDetailsView(summary.id(), summary.courseId(), summary.classId(), summary.title(),
                summary.description(), summary.status(), summary.availableAt(), summary.dueAt(),
                summary.maxAttempts(), summary.submitted(), summary.score(),
                tutorPolicy, views);
    }

    @Transactional
    public AssignmentSummaryView create(Long courseId, Long userId, CreateAssignmentRequest request) {
        courseAccess.requireTeachingStaff(courseId, userId);
        validateClass(courseId, request.classId());
        Assignment assignment = assignments.save(new Assignment(courseId, request.classId(), userId,
                request.title(), request.description(), request.availableAt(), request.dueAt(),
                request.maxAttempts() == null ? 1 : request.maxAttempts(), policies.write(request.tutorPolicy())));
        return summary(assignment, userId);
    }

    @Transactional
    public AssignmentSummaryView update(Long assignmentId, Long userId, UpdateAssignmentRequest request) {
        Assignment assignment = require(assignmentId);
        courseAccess.requireTeachingStaff(assignment.getCourseId(), userId);
        assignment.update(request.title(), request.description(), request.availableAt(), request.dueAt(),
                request.maxAttempts(), request.tutorPolicy() == null ? null : policies.write(request.tutorPolicy()));
        return summary(assignment, userId);
    }

    @Transactional
    public AssignmentDetailsView transition(Long assignmentId, Long userId, AssignmentStatus target) {
        Assignment assignment = require(assignmentId);
        courseAccess.requireTeacherOrAdmin(assignment.getCourseId(), userId);
        if (target == AssignmentStatus.PUBLISHED) {
            List<AssignmentQuestion> publishableQuestions =
                    questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(assignmentId);
            if (publishableQuestions.isEmpty()) {
                throw new AppException(ErrorCode.CONFLICT,
                        "An assignment needs at least one question before publishing");
            }
            for (AssignmentQuestion question : publishableQuestions) validatePublishableQuestion(question);
            requirePublishableRubric(assignmentId, publishableQuestions);
        }
        assignment.transitionTo(target, Instant.now());
        return get(assignmentId, userId);
    }

    @Transactional
    public AssignmentQuestionView addQuestion(Long assignmentId, Long userId, UpsertQuestionRequest request) {
        Assignment assignment = require(assignmentId);
        courseAccess.requireTeachingStaff(assignment.getCourseId(), userId);
        assignment.requireDraft();
        validateQuestion(assignment.getCourseId(), request);
        AssignmentQuestion question = questions.save(new AssignmentQuestion(assignmentId, assignment.getCourseId(),
                request.knowledgePointId(), request.type(), request.prompt().trim(), json(request.options()),
                request.referenceAnswer(), request.points(), request.orderIndex(), json(request.config())));
        return questionView(question);
    }

    @Transactional
    public AssignmentQuestionView updateQuestion(Long assignmentId, Long questionId, Long userId,
                                                 UpsertQuestionRequest request) {
        Assignment assignment = require(assignmentId);
        courseAccess.requireTeachingStaff(assignment.getCourseId(), userId);
        assignment.requireDraft();
        validateQuestion(assignment.getCourseId(), request);
        AssignmentQuestion question = questions.findByIdAndAssignmentId(questionId, assignmentId)
                .orElseThrow(() -> notFound("Question not found"));
        question.update(request.knowledgePointId(), request.type(), request.prompt().trim(), json(request.options()),
                request.referenceAnswer() == null ? question.getReferenceAnswer() : request.referenceAnswer(),
                request.points(), request.orderIndex(),
                request.config() == null ? question.getConfigJson() : json(request.config()));
        return questionView(question);
    }

    @Transactional
    public void deleteQuestion(Long assignmentId, Long questionId, Long userId) {
        Assignment assignment = require(assignmentId);
        courseAccess.requireTeachingStaff(assignment.getCourseId(), userId);
        assignment.requireDraft();
        AssignmentQuestion question = questions.findByIdAndAssignmentId(questionId, assignmentId)
                .orElseThrow(() -> notFound("Question not found"));
        questions.delete(question);
    }

    @Transactional
    public TutorPolicy updatePolicy(Long assignmentId, Long userId, TutorPolicy policy) {
        Assignment assignment = require(assignmentId);
        courseAccess.requireTeacherOrAdmin(assignment.getCourseId(), userId);
        TutorPolicy current = policies.read(assignment.getTutorPolicyJson());
        TutorPolicy requested = policy == null ? TutorPolicy.defaults() : policy;
        String value = policies.write(new TutorPolicy(requested.allowFullSolutionBeforeSubmit(),
                requested.fullSolutionAfterSubmit(), requested.fullSolutionAfterDue(),
                requested.allowLateSubmission(), requested.enabledOperations(), current.dueAtOverrides()));
        assignment.updateTutorPolicy(value);
        return policies.read(value);
    }

    @Transactional
    public TutorPolicy setExtension(Long assignmentId, Long studentId, Long userId, Instant dueAt) {
        Assignment assignment = require(assignmentId);
        courseAccess.requireTeacherOrAdmin(assignment.getCourseId(), userId);
        CourseMember student = members.findByCourseIdAndUserIdAndStatus(
                        assignment.getCourseId(), studentId, CourseMemberStatus.ACTIVE)
                .filter(member -> member.getRole() == CourseMemberRole.STUDENT)
                .orElseThrow(() -> notFound("Student is not an active course member"));
        if (assignment.getClassId() != null && !assignment.getClassId().equals(student.getClassId())) {
            throw notFound("Student is not assigned to this class");
        }
        if (dueAt != null && assignment.getDueAt() != null && dueAt.isBefore(assignment.getDueAt())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Individual extension cannot be earlier than the assignment deadline");
        }
        String value = policies.withExtension(assignment.getTutorPolicyJson(), studentId, dueAt);
        assignment.updateTutorPolicy(value);
        return policies.read(value);
    }

    @Transactional(readOnly = true)
    public Assignment require(Long assignmentId) {
        return assignments.findById(assignmentId).orElseThrow(() -> notFound("Assignment not found"));
    }

    private AssignmentSummaryView summary(Assignment assignment, Long userId) {
        Submission latest = submissions.findFirstByAssignmentIdAndUserIdOrderByAttemptNoDesc(
                assignment.getId(), userId).orElse(null);
        boolean submitted = latest != null && latest.getStatus() != SubmissionStatus.DRAFT;
        BigDecimal score = latest == null ? null : grades.findBySubmissionId(latest.getId())
                .filter(grade -> grade.getStatus() == GradeStatus.CONFIRMED).map(Grade::getFinalScore).orElse(null);
        Instant effectiveDue = assignment.getDueAt();
        if (!isStaff(assignment.getCourseId(), userId)) {
            effectiveDue = policies.read(assignment.getTutorPolicyJson()).effectiveDueAt(userId, effectiveDue);
        }
        return new AssignmentSummaryView(assignment.getId(), assignment.getCourseId(), assignment.getClassId(),
                assignment.getTitle(), assignment.getDescription(), assignment.getStatus(), assignment.getAvailableAt(),
                effectiveDue, assignment.getMaxSubmissions(), submitted, score);
    }

    private AssignmentQuestionView questionView(AssignmentQuestion question) {
        return new AssignmentQuestionView(question.getId(), question.getQuestionType(), question.getPrompt(),
                stringList(question.getOptionsJson()), question.getMaxScore(), question.getSortOrder(),
                question.getKnowledgePointId());
    }

    private void validateClass(Long courseId, Long classId) {
        if (classId == null) return;
        boolean valid = classes.findById(classId).filter(value -> value.getCourseId().equals(courseId)).isPresent();
        if (!valid) throw notFound("Course class not found");
    }

    private void validateQuestion(Long courseId, UpsertQuestionRequest request) {
        if ((request.type() == QuestionType.SINGLE_CHOICE || request.type() == QuestionType.MULTIPLE_CHOICE)
                && (request.options() == null || request.options().size() < 2)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Choice questions require at least two options");
        }
        if (request.knowledgePointId() != null
                && !knowledgePoints.existsByIdAndCourseId(request.knowledgePointId(), courseId)) {
            throw notFound("Knowledge point not found");
        }
    }

    private void validatePublishableQuestion(AssignmentQuestion question) {
        if (question.getQuestionType() == null || question.getPrompt() == null
                || question.getPrompt().isBlank() || question.getMaxScore() == null
                || question.getMaxScore().signum() <= 0) {
            throw new AppException(ErrorCode.CONFLICT, "Question is incomplete for publication");
        }
        if (question.getQuestionType() == QuestionType.SINGLE_CHOICE
                || question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
            List<String> options = stringList(question.getOptionsJson());
            if (options.size() < 2 || options.stream().anyMatch(value -> value == null || value.isBlank())
                    || new HashSet<>(options).size() != options.size()) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Choice questions require at least two distinct non-blank options");
            }
        }
    }

    private void requirePublishableRubric(Long assignmentId, List<AssignmentQuestion> publishableQuestions) {
        Rubric rubric = rubrics.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.CONFLICT,
                        "An assignment needs a rubric before publishing"));
        if (rubric.getStatus() != RubricStatus.PUBLISHED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "The assignment rubric must be published first");
        }
        List<RubricItem> items = rubricItems.findAllByRubricIdOrderBySortOrderAscIdAsc(rubric.getId());
        if (items.isEmpty()) {
            throw new AppException(ErrorCode.CONFLICT,
                    "An assignment rubric needs at least one item before publishing");
        }
        BigDecimal itemTotal = items.stream().map(RubricItem::getMaxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (itemTotal.compareTo(rubric.getTotalScore()) != 0) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Rubric total score must equal the sum of its item scores");
        }
        BigDecimal questionTotal = publishableQuestions.stream().map(AssignmentQuestion::getMaxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (questionTotal.compareTo(rubric.getTotalScore()) != 0) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Rubric total score must equal the sum of question scores");
        }
    }

    private boolean isStaff(Long courseId, Long userId) {
        return courseAccess.isTeachingStaff(courseId, userId);
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST, "Invalid JSON value");
        }
    }

    private List<String> stringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    @SuppressWarnings("unused")
    private Map<String, Object> objectMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private AppException notFound(String message) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private AppException denied(String message) {
        return new AppException(ErrorCode.ACCESS_DENIED, message);
    }
}
