package com.ustb.seforge.course.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "courses")
public class Course extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "semester_id", nullable = false)
    private Long semesterId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status = CourseStatus.ACTIVE;

    protected Course() {
    }

    public Course(String code, String name, String description, Long semesterId, Long ownerId) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.semesterId = semesterId;
        this.ownerId = ownerId;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getSemesterId() { return semesterId; }
    public Long getOwnerId() { return ownerId; }
    public CourseStatus getStatus() { return status; }

    public void update(String name, String description, CourseStatus status) {
        if (name != null && !name.isBlank()) this.name = name.trim();
        if (description != null) this.description = description.trim();
        if (status != null) this.status = status;
    }

    public void transferOwner(Long ownerId) { this.ownerId = ownerId; }
}
