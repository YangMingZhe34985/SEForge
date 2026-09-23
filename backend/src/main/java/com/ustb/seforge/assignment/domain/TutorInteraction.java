package com.ustb.seforge.assignment.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "tutor_interaction")
public class TutorInteraction extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;
    @Column(name = "question_id")
    private Long questionId;
    @Column(name = "submission_id")
    private Long submissionId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TutorOperation operation;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TutorInteractionStatus status;
    @Column(name = "student_input", columnDefinition = "longtext")
    private String studentInput;
    @Column(name = "tutor_response", columnDefinition = "longtext")
    private String tutorResponse;
    @Column(name = "ai_trace_id")
    private Long aiTraceId;

    protected TutorInteraction() {
    }

    public TutorInteraction(Long courseId, Long assignmentId, Long questionId, Long submissionId,
                            Long userId, TutorOperation operation, String studentInput) {
        this.courseId = courseId;
        this.assignmentId = assignmentId;
        this.questionId = questionId;
        this.submissionId = submissionId;
        this.userId = userId;
        this.operation = operation;
        this.studentInput = studentInput;
        status = TutorInteractionStatus.STARTED;
    }

    public void complete(String response, Long aiTraceId) {
        tutorResponse = response;
        this.aiTraceId = aiTraceId;
        status = TutorInteractionStatus.COMPLETED;
    }

    public void deny(String policyMessage) {
        tutorResponse = policyMessage;
        status = TutorInteractionStatus.DENIED;
    }

    public void fail(String message) {
        tutorResponse = message;
        status = TutorInteractionStatus.FAILED;
    }

    public Long getCourseId() { return courseId; }
    public Long getAssignmentId() { return assignmentId; }
    public Long getQuestionId() { return questionId; }
    public Long getSubmissionId() { return submissionId; }
    public Long getUserId() { return userId; }
    public TutorOperation getOperation() { return operation; }
    public TutorInteractionStatus getStatus() { return status; }
    public String getStudentInput() { return studentInput; }
    public String getTutorResponse() { return tutorResponse; }
    public Long getAiTraceId() { return aiTraceId; }
}
