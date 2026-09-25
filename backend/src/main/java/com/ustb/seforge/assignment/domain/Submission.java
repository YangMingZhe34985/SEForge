package com.ustb.seforge.assignment.domain;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "submission")
public class Submission extends BaseEntity {
    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "class_id")
    private Long classId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;
    @Column(name = "submission_key", length = 64)
    private String submissionKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SubmissionStatus status = SubmissionStatus.DRAFT;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "is_late", nullable = false)
    private boolean late;

    protected Submission() {
    }

    public Submission(Long assignmentId, Long courseId, Long classId, Long userId, int attemptNo) {
        this.assignmentId = assignmentId;
        this.courseId = courseId;
        this.classId = classId;
        this.userId = userId;
        this.attemptNo = attemptNo;
    }

    public void submit(Instant submittedAt, boolean late) {
        requireDraft();
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = submittedAt;
        this.late = late;
    }

    public void bindSubmissionKey(String key) {
        requireDraft();
        submissionKey = key;
    }

    public void markGraded() {
        if (status != SubmissionStatus.SUBMITTED) {
            throw new AppException(ErrorCode.CONFLICT, "Only submitted work can be graded");
        }
        status = SubmissionStatus.GRADED;
    }

    public void requireDraft() {
        if (status != SubmissionStatus.DRAFT) {
            throw new AppException(ErrorCode.CONFLICT, "Submitted answers are immutable");
        }
    }

    public Long getAssignmentId() { return assignmentId; }
    public Long getCourseId() { return courseId; }
    public Long getClassId() { return classId; }
    public Long getUserId() { return userId; }
    public int getAttemptNo() { return attemptNo; }
    public String getSubmissionKey() { return submissionKey; }
    public SubmissionStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public boolean isLate() { return late; }
}
