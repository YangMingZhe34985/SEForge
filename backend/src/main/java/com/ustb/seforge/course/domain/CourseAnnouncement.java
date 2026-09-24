package com.ustb.seforge.course.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "course_announcements", indexes =
        @Index(name = "idx_course_announcements_course", columnList = "course_id,published_at"))
public class CourseAnnouncement extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "author_id", nullable = false)
    private Long authorId;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected CourseAnnouncement() {
    }

    public CourseAnnouncement(Long courseId, Long authorId, String title, String content) {
        this.courseId = courseId;
        this.authorId = authorId;
        this.title = title;
        this.content = content;
        this.publishedAt = Instant.now();
    }

    public Long getCourseId() { return courseId; }
    public Long getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Instant getPublishedAt() { return publishedAt; }

    public void update(String title, String content) {
        this.title = title.trim();
        this.content = content.trim();
    }

    public void withdraw() { publishedAt = null; }
}
