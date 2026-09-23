package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.domain.CourseStatus;
import java.time.Instant;

public record CourseSummaryView(
        Long id,
        String code,
        String name,
        String description,
        Long semesterId,
        String semesterName,
        CourseStatus status,
        CourseMemberRole role,
        long memberCount,
        Instant createdAt) {
}
