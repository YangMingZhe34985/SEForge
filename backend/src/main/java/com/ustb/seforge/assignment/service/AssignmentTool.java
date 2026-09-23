package com.ustb.seforge.assignment.service;

import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.AssignmentQuestion;
import com.ustb.seforge.assignment.repository.AssignmentQuestionRepository;
import com.ustb.seforge.assignment.repository.AssignmentRepository;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentTool {
    private final AssignmentRepository assignments;
    private final AssignmentQuestionRepository questions;
    private final AssignmentAuthorizationService authorization;

    public AssignmentTool(AssignmentRepository assignments, AssignmentQuestionRepository questions,
                          AssignmentAuthorizationService authorization) {
        this.assignments = assignments;
        this.questions = questions;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Context load(Long assignmentId, Long questionId, Long userId) {
        Assignment assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found"));
        authorization.requireStudent(assignment, userId);
        authorization.requireVisible(assignment, userId);
        AssignmentQuestion question = questions.findByIdAndAssignmentId(questionId, assignmentId)
                .orElseThrow(() -> notFound("Question not found"));
        return new Context(assignment, question);
    }

    private AppException notFound(String message) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public record Context(Assignment assignment, AssignmentQuestion question) {
    }
}
