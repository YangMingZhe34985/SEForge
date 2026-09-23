package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.CourseStatus;
import jakarta.validation.constraints.Size;

public record UpdateCourseRequest(
        @Size(min = 1, max = 160) String name,
        @Size(max = 5000) String description,
        CourseStatus status) {
}
