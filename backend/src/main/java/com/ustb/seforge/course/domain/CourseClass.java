package com.ustb.seforge.course.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_classes")
public class CourseClass extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    private Integer capacity;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryClass;

    @Column(nullable = false)
    private boolean active = true;

    protected CourseClass() {
    }

    public CourseClass(Long courseId, String code, String name, Integer capacity, boolean primaryClass) {
        this.courseId = courseId;
        this.code = code;
        this.name = name;
        this.capacity = capacity;
        this.primaryClass = primaryClass;
    }

    public Long getCourseId() { return courseId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Integer getCapacity() { return capacity; }
    public boolean isPrimaryClass() { return primaryClass; }
    public boolean isActive() { return active; }

    public void update(String name, Integer capacity, boolean primaryClass) {
        this.name = name.trim();
        this.capacity = capacity;
        this.primaryClass = primaryClass;
    }

    public void close() { this.active = false; }
    public void reopen() { this.active = true; }
}
