package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.CourseMemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateCourseMemberRequest(Long classId, @NotNull CourseMemberRole role) {
}
