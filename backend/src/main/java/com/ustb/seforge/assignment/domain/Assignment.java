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
@Table(name = "assignment")
public class Assignment extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "longtext")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Column(name = "available_at")
    private Instant availableAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "max_submissions", nullable = false)
    private int maxSubmissions = 1;

    @Column(name = "tutor_policy", columnDefinition = "json")
    private String tutorPolicyJson;

    protected Assignment() {
    }

    public Assignment(Long courseId, Long classId, Long createdBy, String title, String description,
                      Instant availableAt, Instant dueAt, int maxSubmissions, String tutorPolicyJson) {
        this.courseId = courseId;
        this.classId = classId;
        this.createdBy = createdBy;
        this.title = required(title, "Assignment title is required");
        this.description = description;
        this.availableAt = availableAt;
        this.dueAt = dueAt;
        this.maxSubmissions = positive(maxSubmissions);
        this.tutorPolicyJson = tutorPolicyJson;
        validateDates();
    }

    public void update(String title, String description, Instant availableAt, Instant dueAt,
                       Integer maxSubmissions, String tutorPolicyJson) {
        requireDraft();
        if (title != null) this.title = required(title, "Assignment title is required");
        if (description != null) this.description = description;
        this.availableAt = availableAt;
        this.dueAt = dueAt;
        if (maxSubmissions != null) this.maxSubmissions = positive(maxSubmissions);
        if (tutorPolicyJson != null) this.tutorPolicyJson = tutorPolicyJson;
        validateDates();
    }

    public void updateTutorPolicy(String tutorPolicyJson) {
        if (status == AssignmentStatus.ARCHIVED) {
            throw conflict("Archived assignments cannot be changed");
        }
        this.tutorPolicyJson = tutorPolicyJson;
    }

    public void transitionTo(AssignmentStatus target, Instant now) {
        if (target == null || target == status) return;
        boolean allowed = switch (status) {
            case DRAFT -> target == AssignmentStatus.PUBLISHED || target == AssignmentStatus.ARCHIVED;
            case PUBLISHED -> target == AssignmentStatus.CLOSED || target == AssignmentStatus.ARCHIVED;
            case CLOSED -> target == AssignmentStatus.PUBLISHED || target == AssignmentStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) throw conflict("Invalid assignment transition: " + status + " -> " + target);
        status = target;
        closedAt = target == AssignmentStatus.CLOSED || target == AssignmentStatus.ARCHIVED ? now : null;
    }

    public boolean isAvailableAt(Instant now) {
        return status == AssignmentStatus.PUBLISHED && (availableAt == null || !availableAt.isAfter(now));
    }

    public void requireDraft() {
        if (status != AssignmentStatus.DRAFT) throw conflict("Only draft assignments can be edited");
    }

    private void validateDates() {
        if (availableAt != null && dueAt != null && !dueAt.isAfter(availableAt)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Due time must be after availability time");
        }
    }

    private static int positive(int value) {
        if (value < 1) throw new AppException(ErrorCode.VALIDATION_FAILED, "maxSubmissions must be positive");
        return value;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new AppException(ErrorCode.VALIDATION_FAILED, message);
        return value.trim();
    }

    private static AppException conflict(String message) {
        return new AppException(ErrorCode.CONFLICT, message);
    }

    public Long getCourseId() { return courseId; }
    public Long getClassId() { return classId; }
    public Long getCreatedBy() { return createdBy; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public AssignmentStatus getStatus() { return status; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getDueAt() { return dueAt; }
    public Instant getClosedAt() { return closedAt; }
    public int getMaxSubmissions() { return maxSubmissions; }
    public String getTutorPolicyJson() { return tutorPolicyJson; }
}
