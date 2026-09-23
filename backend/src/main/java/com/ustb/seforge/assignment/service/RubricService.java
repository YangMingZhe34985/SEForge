package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.api.RubricItemView;
import com.ustb.seforge.assignment.api.RubricView;
import com.ustb.seforge.assignment.api.UpsertRubricItemRequest;
import com.ustb.seforge.assignment.api.UpsertRubricRequest;
import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.Rubric;
import com.ustb.seforge.assignment.domain.RubricItem;
import com.ustb.seforge.assignment.domain.RubricStatus;
import com.ustb.seforge.assignment.repository.AssignmentQuestionRepository;
import com.ustb.seforge.assignment.repository.AssignmentRepository;
import com.ustb.seforge.assignment.repository.RubricItemRepository;
import com.ustb.seforge.assignment.repository.RubricRepository;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.course.domain.CourseMemberRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RubricService {
    private final AssignmentRepository assignments;
    private final AssignmentQuestionRepository questions;
    private final RubricRepository rubrics;
    private final RubricItemRepository items;
    private final CourseAccessService courseAccess;
    private final AssignmentAuthorizationService authorization;
    private final ObjectMapper objectMapper;

    public RubricService(AssignmentRepository assignments, AssignmentQuestionRepository questions,
                         RubricRepository rubrics, RubricItemRepository items,
                         CourseAccessService courseAccess, AssignmentAuthorizationService authorization,
                         ObjectMapper objectMapper) {
        this.assignments = assignments;
        this.questions = questions;
        this.rubrics = rubrics;
        this.items = items;
        this.courseAccess = courseAccess;
        this.authorization = authorization;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RubricView get(Long assignmentId, Long userId) {
        Assignment assignment = requireAssignment(assignmentId);
        authorization.requireVisible(assignment, userId);
        Rubric rubric = rubrics.findByAssignmentId(assignmentId).orElse(null);
        if (rubric == null || (rubric.getStatus() == RubricStatus.DRAFT
                && !isStaff(assignment.getCourseId(), userId))) {
            return null;
        }
        return view(rubric);
    }

    @Transactional
    public RubricView upsert(Long assignmentId, Long userId, UpsertRubricRequest request) {
        Assignment assignment = requireAssignment(assignmentId);
        courseAccess.requireTeachingStaff(assignment.getCourseId(), userId);
        assignment.requireDraft();
        Optional<Rubric> existing = rubrics.findByAssignmentId(assignmentId);
        Rubric rubric = existing.orElseGet(() ->
                new Rubric(assignmentId, request.title(), request.totalScore()));
        List<RubricItem> currentItems = existing.isPresent()
                ? items.findAllByRubricIdOrderBySortOrderAscIdAsc(rubric.getId()) : List.of();
        RubricStatus targetStatus = request.status() == null ? rubric.getStatus() : request.status();
        if (currentItems.isEmpty() && targetStatus != RubricStatus.DRAFT) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "A rubric needs at least one item before it can be published or archived");
        }
        if (targetStatus != RubricStatus.DRAFT) {
            validateExactTotal(request.totalScore(), currentItems);
        }
        rubric.update(request.title(), request.totalScore(), request.status());
        return view(rubrics.save(rubric));
    }

    @Transactional
    public RubricItemView addItem(Long assignmentId, Long userId, UpsertRubricItemRequest request) {
        Assignment assignment = requireAssignment(assignmentId);
        courseAccess.requireTeachingStaff(assignment.getCourseId(), userId);
        assignment.requireDraft();
        Rubric rubric = rubrics.findByAssignmentId(assignmentId)
                .orElseThrow(() -> notFound("Rubric not found"));
        requireEditable(rubric);
        validateQuestion(assignmentId, request.questionId());
        RubricItem item = items.save(new RubricItem(rubric.getId(), request.questionId(), request.title(),
                request.description(), request.maxScore(), json(request.criteria()), request.orderIndex()));
        return itemView(item);
    }

    @Transactional
    public RubricItemView updateItem(Long assignmentId, Long itemId, Long userId,
                                     UpsertRubricItemRequest request) {
        Assignment assignment = requireAssignment(assignmentId);
        courseAccess.requireTeachingStaff(assignment.getCourseId(), userId);
        assignment.requireDraft();
        Rubric rubric = rubrics.findByAssignmentId(assignmentId)
                .orElseThrow(() -> notFound("Rubric not found"));
        requireEditable(rubric);
        validateQuestion(assignmentId, request.questionId());
        RubricItem item = items.findByIdAndRubricId(itemId, rubric.getId())
                .orElseThrow(() -> notFound("Rubric item not found"));
        item.update(request.questionId(), request.title(), request.description(), request.maxScore(),
                json(request.criteria()), request.orderIndex());
        return itemView(item);
    }

    @Transactional
    public void deleteItem(Long assignmentId, Long itemId, Long userId) {
        Assignment assignment = requireAssignment(assignmentId);
        courseAccess.requireTeachingStaff(assignment.getCourseId(), userId);
        assignment.requireDraft();
        Rubric rubric = rubrics.findByAssignmentId(assignmentId)
                .orElseThrow(() -> notFound("Rubric not found"));
        requireEditable(rubric);
        RubricItem item = items.findByIdAndRubricId(itemId, rubric.getId())
                .orElseThrow(() -> notFound("Rubric item not found"));
        items.delete(item);
    }

    private RubricView view(Rubric rubric) {
        return new RubricView(rubric.getId(), rubric.getAssignmentId(), rubric.getTitle(), rubric.getTotalScore(),
                rubric.getStatus(), items.findAllByRubricIdOrderBySortOrderAscIdAsc(rubric.getId())
                .stream().map(this::itemView).toList());
    }

    private RubricItemView itemView(RubricItem item) {
        return new RubricItemView(item.getId(), item.getQuestionId(), item.getTitle(), item.getDescription(),
                item.getMaxScore(), map(item.getCriteriaJson()), item.getSortOrder());
    }

    private void validateQuestion(Long assignmentId, Long questionId) {
        if (questionId != null && questions.findByIdAndAssignmentId(questionId, assignmentId).isEmpty()) {
            throw notFound("Question not found");
        }
    }

    private void requireEditable(Rubric rubric) {
        if (rubric.getStatus() != RubricStatus.DRAFT) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Only draft rubrics can be edited");
        }
    }

    private void validateExactTotal(BigDecimal requestedTotal, List<RubricItem> rubricItems) {
        if (!rubricItems.isEmpty() && itemTotal(rubricItems).compareTo(requestedTotal) != 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Rubric total score must equal the sum of its item scores");
        }
    }

    private BigDecimal itemTotal(List<RubricItem> rubricItems) {
        return rubricItems.stream().map(RubricItem::getMaxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Assignment requireAssignment(Long assignmentId) {
        return assignments.findById(assignmentId).orElseThrow(() -> notFound("Assignment not found"));
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST, "Invalid rubric criteria");
        }
    }

    private Map<String, Object> map(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private boolean isStaff(Long courseId, Long userId) {
        if (courseAccess.isAdmin(userId)) return true;
        return courseAccess.roleFor(courseId, userId)
                .map(role -> role == CourseMemberRole.TEACHER || role == CourseMemberRole.TA)
                .orElse(false);
    }

    private AppException notFound(String message) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
