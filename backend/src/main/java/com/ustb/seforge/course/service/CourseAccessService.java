package com.ustb.seforge.course.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.domain.CourseMember;
import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.domain.CourseMemberStatus;
import com.ustb.seforge.course.repository.CourseMemberRepository;
import com.ustb.seforge.identity.repository.UserRoleRepository;
import com.ustb.seforge.identity.repository.UserProfileRepository;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.security.UserPrincipal;
import java.util.EnumSet;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("courseAccess")
public class CourseAccessService {
    private final CourseMemberRepository memberRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileRepository profileRepository;

    public CourseAccessService(
            CourseMemberRepository memberRepository, UserRoleRepository userRoleRepository,
            UserProfileRepository profileRepository) {
        this.memberRepository = memberRepository;
        this.userRoleRepository = userRoleRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public void requireMember(Long courseId, Long userId) {
        if (isAdmin(userId)) return;
        memberRepository.findByCourseIdAndUserIdAndStatus(courseId, userId, CourseMemberStatus.ACTIVE)
                .orElseThrow(this::accessDenied);
    }

    @Transactional(readOnly = true)
    public void requireTeachingStaff(Long courseId, Long userId) {
        if (!isTeachingStaff(courseId, userId)) throw accessDenied();
    }

    @Transactional(readOnly = true)
    public boolean isTeachingStaff(Long courseId, Long userId) {
        return userId != null && (!isAdmin(userId) || isTeacherAccount(userId))
                && memberRepository.existsByCourseIdAndUserIdAndRoleInAndStatus(
                courseId, userId, EnumSet.of(CourseMemberRole.TEACHER, CourseMemberRole.TA),
                CourseMemberStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public void requireTeacherOrAdmin(Long courseId, Long userId) {
        requireAdminTeachingQualification(userId);
        boolean allowed = memberRepository.existsByCourseIdAndUserIdAndRoleInAndStatus(
                courseId, userId, EnumSet.of(CourseMemberRole.TEACHER), CourseMemberStatus.ACTIVE);
        if (!allowed) throw accessDenied();
    }

    @Transactional(readOnly = true)
    public Optional<CourseMemberRole> roleFor(Long courseId, Long userId) {
        return memberRepository.findByCourseIdAndUserIdAndStatus(courseId, userId, CourseMemberStatus.ACTIVE)
                .map(CourseMember::getRole);
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(Long userId) {
        return userRoleRepository.existsByUserIdAndRoleCode(userId, "ADMIN");
    }

    @Transactional(readOnly = true)
    public boolean canView(Long courseId, Authentication authentication) {
        Long userId = principalId(authentication);
        return userId != null && (isAdmin(userId)
                || memberRepository.existsByCourseIdAndUserIdAndStatus(
                        courseId, userId, CourseMemberStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    public boolean canManage(Long courseId, Authentication authentication) {
        Long userId = principalId(authentication);
        return userId != null && (!isAdmin(userId) || isTeacherAccount(userId))
                && memberRepository.existsByCourseIdAndUserIdAndRoleInAndStatus(
                        courseId, userId, EnumSet.of(CourseMemberRole.TEACHER, CourseMemberRole.TA),
                        CourseMemberStatus.ACTIVE);
    }

    private void requireAdminTeachingQualification(Long userId) {
        if (isAdmin(userId) && !isTeacherAccount(userId)) throw accessDenied();
    }

    private boolean isTeacherAccount(Long userId) {
        return profileRepository.findByUserId(userId)
                .map(profile -> profile.getAccountType() == AccountType.TEACHER).orElse(false);
    }

    private Long principalId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal.userId();
    }

    private AppException accessDenied() {
        return new AppException(ErrorCode.ACCESS_DENIED, "You do not have access to this course");
    }
}
