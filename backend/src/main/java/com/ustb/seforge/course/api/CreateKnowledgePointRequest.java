package com.ustb.seforge.course.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKnowledgePointRequest(
        Long chapterId,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 5000) String description,
        @Min(0) int sortOrder) {
}
