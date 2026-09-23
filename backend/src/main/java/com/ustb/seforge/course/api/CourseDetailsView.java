package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.domain.CourseStatus;
import java.time.Instant;
import java.util.List;

public record CourseDetailsView(
        Long id,
        String code,
        String name,
        String description,
        Long semesterId,
        String semesterName,
        Long ownerId,
        CourseStatus status,
        CourseMemberRole role,
        long memberCount,
        List<CourseClassView> classes,
        Instant createdAt) {
}
