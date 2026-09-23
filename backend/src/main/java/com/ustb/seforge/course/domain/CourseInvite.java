package com.ustb.seforge.course.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "course_invites")
public class CourseInvite extends BaseEntity {
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "class_id")
    private Long classId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 20)
    private CourseMemberRole memberRole;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    protected CourseInvite() {
    }

    public CourseInvite(
            String code, Long courseId, Long classId, CourseMemberRole memberRole,
            Integer maxUses, Instant expiresAt, Long createdBy) {
        this.code = code;
        this.courseId = courseId;
        this.classId = classId;
        this.memberRole = memberRole;
        this.maxUses = maxUses;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public String getCode() { return code; }
    public Long getCourseId() { return courseId; }
    public Long getClassId() { return classId; }
    public CourseMemberRole getMemberRole() { return memberRole; }
    public Integer getMaxUses() { return maxUses; }
    public int getUsedCount() { return usedCount; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }
    public Long getCreatedBy() { return createdBy; }

    public boolean isExpired(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }
    public boolean isExhausted() { return maxUses != null && usedCount >= maxUses; }
    public void consume() {
        usedCount++;
        if (maxUses != null && usedCount >= maxUses) active = false;
    }
}
