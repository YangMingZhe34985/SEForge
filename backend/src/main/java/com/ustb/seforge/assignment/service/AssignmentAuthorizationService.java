package com.ustb.seforge.assignment.service;

import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.AssignmentStatus;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.domain.CourseMember;
import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.domain.CourseMemberStatus;
import com.ustb.seforge.course.repository.CourseMemberRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentAuthorizationService {
    private final CourseAccessService courseAccess;
    private final CourseMemberRepository members;

    public AssignmentAuthorizationService(CourseAccessService courseAccess, CourseMemberRepository members) {
        this.courseAccess = courseAccess;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public CourseMember requireStudent(Assignment assignment, Long userId) {
        courseAccess.requireMember(assignment.getCourseId(), userId);
        CourseMember member = members.findByCourseIdAndUserIdAndStatus(
                        assignment.getCourseId(), userId, CourseMemberStatus.ACTIVE)
                .orElseThrow(this::denied);
        if (member.getRole() != CourseMemberRole.STUDENT || !matchesClass(assignment, member)) {
            throw denied();
        }
        return member;
    }

    @Transactional(readOnly = true)
    public void requireVisible(Assignment assignment, Long userId) {
        courseAccess.requireMember(assignment.getCourseId(), userId);
        if (courseAccess.isAdmin(userId)) return;
        CourseMember member = members.findByCourseIdAndUserIdAndStatus(
                        assignment.getCourseId(), userId, CourseMemberStatus.ACTIVE)
                .orElseThrow(this::denied);
        if (member.getRole() == CourseMemberRole.TEACHER || member.getRole() == CourseMemberRole.TA) return;
        if (!matchesClass(assignment, member)
                || (assignment.getStatus() != AssignmentStatus.PUBLISHED
                && assignment.getStatus() != AssignmentStatus.CLOSED)
                || (assignment.getStatus() == AssignmentStatus.PUBLISHED
                && assignment.getAvailableAt() != null && assignment.getAvailableAt().isAfter(Instant.now()))) {
            throw denied();
        }
    }

    private boolean matchesClass(Assignment assignment, CourseMember member) {
        return assignment.getClassId() == null || assignment.getClassId().equals(member.getClassId());
    }

    private AppException denied() {
        return new AppException(ErrorCode.ACCESS_DENIED, "You do not have access to this assignment");
    }
}
