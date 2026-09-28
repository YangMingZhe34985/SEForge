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
    private final AssignmentMediaService media;

    public AssignmentService(AssignmentRepository assignments, AssignmentQuestionRepository questions,
                             SubmissionRepository submissions, GradeRepository grades,
                             RubricRepository rubrics, RubricItemRepository rubricItems,
                             CourseMemberRepository members, CourseClassRepository classes,
                             KnowledgePointRepository knowledgePoints,
                             CourseAccessService courseAccess, AssignmentAuthorizationService authorization,
                             TutorPolicyCodec policies, ObjectMapper objectMapper, AssignmentMediaService media) {
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
        this.media = media;
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
                .stream().map(q -> questionView(q, isStaff(assignment.getCourseId(), userId))).toList();
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
        Assignment assignment = assignments.findByIdForUpdate(assignmentId).orElseThrow(()->notFound("Assignment not found"));
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
        validateContent(assignmentId, request);
        AssignmentQuestion question = questions.save(new AssignmentQuestion(assignmentId, assignment.getCourseId(),
                request.knowledgePointId(), request.type(), request.prompt().trim(), json(request.options()),
                request.referenceAnswer(), request.points(), request.orderIndex(), json(normalizedConfig(assignmentId,request))));
        return questionView(question, true);
    }

    @Transactional
    public AssignmentQuestionView updateQuestion(Long assignmentId, Long questionId, Long userId,
                                                 UpsertQuestionRequest request) {
        Assignment assignment = assignments.findByIdForUpdate(assignmentId).orElseThrow(() -> notFound("Assignment not found"));
        courseAccess.requireTeachingStaff(assignment.getCourseId(), userId);
        assignment.requireDraft();
        validateQuestion(assignment.getCourseId(), request);
        validateContent(assignmentId, request);
        AssignmentQuestion question = questions.findByIdAndAssignmentId(questionId, assignmentId)
                .orElseThrow(() -> notFound("Question not found"));
        var allocated = rubrics.findByAssignmentId(assignmentId).map(r -> rubricItems.findAllByRubricIdOrderBySortOrderAscIdAsc(r.getId()).stream()
                .filter(i -> questionId.equals(i.getQuestionId())).map(RubricItem::getMaxScore).reduce(BigDecimal.ZERO,BigDecimal::add)).orElse(BigDecimal.ZERO);
        if (allocated.compareTo(request.points()) > 0) throw QuestionContent.invalid("题目分值不能低于已分配的 Rubric 分值，请先调整分项");
        question.update(request.knowledgePointId(), request.type(), request.prompt().trim(), json(request.options()),
                request.referenceAnswer() == null ? question.getReferenceAnswer() : request.referenceAnswer(),
                request.points(), request.orderIndex(),
                request.config() == null ? question.getConfigJson() : json(normalizedConfig(assignmentId,request)));
        return questionView(question, true);
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
                .filter(grade -> grade.getStatus() == GradeStatus.PUBLISHED).map(Grade::getFinalScore).orElse(null);
        Instant effectiveDue = assignment.getDueAt();
        if (!isStaff(assignment.getCourseId(), userId)) {
            effectiveDue = policies.read(assignment.getTutorPolicyJson()).effectiveDueAt(userId, effectiveDue);
        }
        return new AssignmentSummaryView(assignment.getId(), assignment.getCourseId(), assignment.getClassId(),
                assignment.getTitle(), assignment.getDescription(), assignment.getStatus(), assignment.getAvailableAt(),
                effectiveDue, assignment.getMaxSubmissions(), submitted, score);
    }

    private AssignmentQuestionView questionView(AssignmentQuestion question, boolean staff) {
        Map<String,Object> config = new java.util.LinkedHashMap<>(objectMap(question.getConfigJson()));
        if (!staff) {
            if(config.containsKey("assetIds"))config.put("assetIds",media.publicContentIds(question.getAssignmentId(),QuestionContent.ids(QuestionContent.config(question).path("assetIds"))));
            config.remove("answerSpec");
            var spec = QuestionContent.config(question).path("answerSpec");
            if (spec.has("allowedFileTypes")) config.put("answerSpec", Map.of("allowedFileTypes", spec.get("allowedFileTypes")));
        }
        return new AssignmentQuestionView(question.getId(), question.getQuestionType(), question.getPrompt(),
                stringList(question.getOptionsJson()), question.getMaxScore(), question.getSortOrder(),
                question.getKnowledgePointId(), config, staff ? question.getReferenceAnswer() : null);
    }

    private void validateContent(Long assignmentId, UpsertQuestionRequest request) {
        if(request.prompt().isBlank() && (request.config()==null || QuestionContent.ids(objectMapper.valueToTree(request.config()).path("assetIds")).isEmpty()))
            throw QuestionContent.invalid("题干正文或题目附件至少填写一项");
        if(request.config()==null || request.config().isEmpty())return;
        var config=objectMapper.valueToTree(request.config());
        if(json(request.config()).length()>150_000)throw QuestionContent.invalid("Question content is too large");
        if(!java.util.Set.of("schemaVersion","assetIds","choices","answerSpec","language","constraints","examples","codeBlocks","knowledgePointIds","gradingMode").containsAll(request.config().keySet()))
            throw QuestionContent.invalid("Unsupported question content field");
        for(Long id:QuestionContent.ids(config.path("assetIds")))media.bound(id,assignmentId,com.ustb.seforge.assignment.domain.AssignmentMedia.Purpose.CONTENT,null,null);
        for(Long id:QuestionContent.ids(config.path("answerSpec").path("assetIds")))media.bound(id,assignmentId,com.ustb.seforge.assignment.domain.AssignmentMedia.Purpose.REFERENCE,null,null);
        var assignment=require(assignmentId);
        for(Long id:QuestionContent.ids(config.path("knowledgePointIds")))if(!knowledgePoints.existsByIdAndCourseId(id,assignment.getCourseId()))throw notFound("Knowledge point not found");
        if(config.has("choices")) {
            if(!config.path("choices").isArray()||config.path("choices").size()<2||config.path("choices").size()>30)throw QuestionContent.invalid("Provide 2–30 choices");
            var ids=new java.util.HashSet<String>();
            for(var choice:config.path("choices"))if(!choice.path("id").asText().matches("[A-Za-z0-9_-]{1,64}") || choice.path("label").asText().isBlank() || !ids.add(choice.path("id").asText()))throw QuestionContent.invalid("Choice IDs and labels are required and IDs must be unique");
            if(request.options()==null||!ids.equals(new java.util.HashSet<>(request.options())))throw QuestionContent.invalid("Options must match structured choice IDs");
        }
        var allowed=config.path("answerSpec").path("allowedFileTypes");
        if(!allowed.isMissingNode()) {
            if(!allowed.isArray()||allowed.isEmpty())throw QuestionContent.invalid("Select allowed report file types");
            for(var ext:allowed)if(!java.util.Set.of("pdf","docx","md","txt","png","jpg","jpeg").contains(ext.asText()))throw QuestionContent.invalid("Unsupported report file type");
        }
        normalizedConfig(assignmentId,request);
    }

    private Map<String,Object> normalizedConfig(Long assignmentId,UpsertQuestionRequest request) {
        var result=new java.util.LinkedHashMap<String,Object>(request.config()==null?Map.of():request.config());
        if(result.isEmpty())return result;
        String mode=String.valueOf(result.getOrDefault("gradingMode",request.type().objective()?"RULE":"AI_ASSISTED"));
        if(!java.util.Set.of("RULE","AI_ASSISTED","MANUAL").contains(mode))throw QuestionContent.invalid("Invalid grading mode");
        if(request.type().objective() && !mode.equals("RULE") || !request.type().objective() && mode.equals("RULE"))throw QuestionContent.invalid("RULE 仅用于客观题，客观题必须 RULE");
        if(!request.type().objective()) {
            var config=objectMapper.valueToTree(result);
            boolean imageOnlyReference=(request.referenceAnswer()==null||request.referenceAnswer().isBlank())
                    && !QuestionContent.ids(config.path("answerSpec").path("assetIds")).isEmpty()
                    && QuestionContent.ids(config.path("answerSpec").path("assetIds")).stream().allMatch(id->media.bound(id,assignmentId,com.ustb.seforge.assignment.domain.AssignmentMedia.Purpose.REFERENCE,null,null).getMediaType().startsWith("image/"));
            if(request.prompt().isBlank() || imageOnlyReference)mode="MANUAL";
        }
        result.put("gradingMode",mode);return result;
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
        QuestionContent.validateStandard(question);
        // New structured questions must have an unambiguous standard before publication.
        if (QuestionContent.config(question).path("schemaVersion").asInt()==1) {
            QuestionContent.validateStandard(question);
            if(question.getQuestionType()==QuestionType.CODE && QuestionContent.config(question).path("language").asText().isBlank())
                throw QuestionContent.invalid("CODE questions require a source language");
            if(question.getQuestionType()==QuestionType.DOCUMENT_REPORT && !QuestionContent.config(question).path("answerSpec").path("allowedFileTypes").isArray())
                throw QuestionContent.invalid("Report questions require allowed file types");
        }
        if (question.getQuestionType() == null || question.getPrompt() == null
                || (question.getPrompt().isBlank() && QuestionContent.ids(QuestionContent.config(question).path("assetIds")).isEmpty()) || question.getMaxScore() == null
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
        boolean needsRubric = publishableQuestions.stream().anyMatch(q -> QuestionGrading.mode(q) == QuestionGrading.Mode.AI_ASSISTED);
        Rubric rubric = rubrics.findByAssignmentId(assignmentId).orElse(null);
        if (rubric == null) {
            if (needsRubric) throw new AppException(ErrorCode.CONFLICT, "AI_ASSISTED 题目必须配置 Rubric");
            return;
        }
        List<RubricItem> items = rubricItems.findAllByRubricIdOrderBySortOrderAscIdAsc(rubric.getId());
        if (items.isEmpty() && !needsRubric) return;
        if (needsRubric && rubric.getStatus() != RubricStatus.PUBLISHED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "The assignment rubric must be published first");
        }
        if (items.isEmpty()) {
            throw new AppException(ErrorCode.CONFLICT,
                    "An assignment rubric needs at least one item before publishing");
        }
        BigDecimal itemTotal = items.stream().map(RubricItem::getMaxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (needsRubric && itemTotal.compareTo(rubric.getTotalScore()) != 0) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Rubric total score must equal the sum of its item scores");
        }
        var ids = publishableQuestions.stream().map(AssignmentQuestion::getId).collect(java.util.stream.Collectors.toSet());
        if(items.stream().anyMatch(i -> i.getQuestionId() == null || !ids.contains(i.getQuestionId())))
            throw QuestionContent.invalid("每个 Rubric 分项必须关联当前作业题目");
        for(var question:publishableQuestions) {
            var sum=items.stream().filter(i->question.getId().equals(i.getQuestionId())).map(RubricItem::getMaxScore).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(sum.compareTo(question.getMaxScore()) > 0) throw QuestionContent.invalid("Rubric 已分配分值超过题目分值");
            if(QuestionGrading.mode(question) == QuestionGrading.Mode.AI_ASSISTED && sum.compareTo(question.getMaxScore()) != 0)
                throw QuestionContent.invalid("AI_ASSISTED 题目 Rubric 分项总分必须等于题目分值");
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
