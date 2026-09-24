package com.ustb.seforge.course.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "course_members")
public class CourseMember extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseMemberStatus status = CourseMemberStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    protected CourseMember() {
    }

    public CourseMember(Long courseId, Long classId, Long userId, CourseMemberRole role) {
        this.courseId = courseId;
        this.classId = classId;
        this.userId = userId;
        this.role = role;
    }

    public Long getCourseId() { return courseId; }
    public Long getClassId() { return classId; }
    public Long getUserId() { return userId; }
    public CourseMemberRole getRole() { return role; }
    public CourseMemberStatus getStatus() { return status; }
    public Instant getJoinedAt() { return joinedAt; }

    public void remove() { this.status = CourseMemberStatus.REMOVED; }
    public void update(Long classId, CourseMemberRole role) {
        this.classId = classId;
        this.role = role;
    }
    public void reactivate(Long classId, CourseMemberRole role) {
        this.classId = classId;
        this.role = role;
        this.status = CourseMemberStatus.ACTIVE;
        this.joinedAt = Instant.now();
    }
}
