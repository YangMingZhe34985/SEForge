package com.ustb.seforge.course.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_resources")
public class CourseResource extends BaseEntity {
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "chapter_id")
    private Long chapterId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    private String objectKey;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceStatus status = ResourceStatus.ACTIVE;

    protected CourseResource() {
    }

    public CourseResource(
            Long courseId, Long chapterId, Long uploaderId, String name, String description,
            ResourceType resourceType, String objectKey, String contentType, Long sizeBytes) {
        this.courseId = courseId;
        this.chapterId = chapterId;
        this.uploaderId = uploaderId;
        this.name = name;
        this.description = description;
        this.resourceType = resourceType;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public Long getCourseId() { return courseId; }
    public Long getChapterId() { return chapterId; }
    public Long getUploaderId() { return uploaderId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ResourceType getResourceType() { return resourceType; }
    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public ResourceStatus getStatus() { return status; }
}
