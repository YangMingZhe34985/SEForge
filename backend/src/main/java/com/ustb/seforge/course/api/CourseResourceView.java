package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.ResourceType;
import java.time.Instant;

public record CourseResourceView(
        Long id,
        Long courseId,
        Long chapterId,
        Long uploaderId,
        String name,
        String description,
        ResourceType resourceType,
        String objectKey,
        String contentType,
        Long sizeBytes,
        Instant createdAt) {
}
