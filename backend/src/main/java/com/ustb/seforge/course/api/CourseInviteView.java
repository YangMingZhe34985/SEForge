package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.CourseMemberRole;
import java.time.Instant;

public record CourseInviteView(
        Long id,
        String code,
        Long courseId,
        Long classId,
        CourseMemberRole memberRole,
        Integer maxUses,
        int usedCount,
        Instant expiresAt,
        boolean active) {
}
