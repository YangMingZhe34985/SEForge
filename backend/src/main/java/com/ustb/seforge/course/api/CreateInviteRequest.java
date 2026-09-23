package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.CourseMemberRole;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record CreateInviteRequest(
        Long classId,
        CourseMemberRole memberRole,
        @Positive Integer maxUses,
        @Future Instant expiresAt) {
}
