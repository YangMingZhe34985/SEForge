package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.CourseMemberRole;

public record AdminUserMembershipView(Long courseId, String courseName, Long classId,
                                      CourseMemberRole role) {
}
