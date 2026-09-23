package com.ustb.seforge.course.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_points")
public class KnowledgePoint extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "chapter_id")
    private Long chapterId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected KnowledgePoint() {
    }

    public KnowledgePoint(Long courseId, Long chapterId, String title, String description, int sortOrder) {
        this.courseId = courseId;
        this.chapterId = chapterId;
        this.title = title;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public Long getCourseId() { return courseId; }
    public Long getChapterId() { return chapterId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
}
