package com.ustb.seforge.course.api;

import jakarta.validation.constraints.NotNull;

public record TransferCourseOwnerRequest(@NotNull Long userId) {
}
