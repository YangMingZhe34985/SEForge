package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateCourseResourceRequest(
        Long chapterId,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 5000) String description,
        @NotNull ResourceType resourceType,
        @NotBlank @Size(max = 512) String objectKey,
        @Size(max = 150) String contentType,
        @PositiveOrZero Long sizeBytes) {
}
