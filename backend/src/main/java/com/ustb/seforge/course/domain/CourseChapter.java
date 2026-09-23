package com.ustb.seforge.course.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_chapters")
public class CourseChapter extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CourseChapter() {
    }

    public CourseChapter(Long courseId, Long parentId, String title, String description, int sortOrder) {
        this.courseId = courseId;
        this.parentId = parentId;
        this.title = title;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public Long getCourseId() { return courseId; }
    public Long getParentId() { return parentId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
}
