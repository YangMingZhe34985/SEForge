package com.ustb.seforge.course.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCourseClassRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$") String code,
        @NotBlank @Size(max = 120) String name,
        @Positive Integer capacity,
        boolean primaryClass) {
}
