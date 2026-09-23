package com.ustb.seforge.course.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinCourseRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6,32}$") String inviteCode) {
}
