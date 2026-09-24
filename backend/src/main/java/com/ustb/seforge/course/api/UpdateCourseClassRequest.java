package com.ustb.seforge.course.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateCourseClassRequest(
        @NotBlank @Size(max = 120) String name,
        @Positive Integer capacity,
        boolean primaryClass,
        boolean active) {
}
