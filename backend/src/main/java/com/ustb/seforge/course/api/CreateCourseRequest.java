package com.ustb.seforge.course.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{2,64}$") String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 5000) String description,
        @NotNull Long semesterId) {
}
