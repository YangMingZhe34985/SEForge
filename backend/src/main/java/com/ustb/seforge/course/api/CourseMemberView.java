package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.CourseMemberRole;
import java.time.Instant;

public record CourseMemberView(
        Long id,
        Long userId,
        String displayName,
        Long classId,
        CourseMemberRole role,
        Instant joinedAt) {
}
