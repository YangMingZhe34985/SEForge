package com.ustb.seforge.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.domain.CourseMemberStatus;
import com.ustb.seforge.course.repository.CourseMemberRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.identity.repository.UserRoleRepository;
import com.ustb.seforge.identity.repository.UserProfileRepository;
import com.ustb.seforge.identity.domain.UserProfile;
import com.ustb.seforge.identity.domain.AccountType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseAccessServiceTest {
    @Mock CourseMemberRepository memberRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock UserProfileRepository profileRepository;

    private CourseAccessService service;

    @BeforeEach
    void setUp() {
        service = new CourseAccessService(memberRepository, userRoleRepository, profileRepository);
    }

    @Test
    void nonMemberCannotReadCourse() {
        when(userRoleRepository.existsByUserIdAndRoleCode(7L, "ADMIN")).thenReturn(false);
        when(memberRepository.findByCourseIdAndUserIdAndStatus(11L, 7L, CourseMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireMember(11L, 7L))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void administratorCanReadCourseWithoutMembership() {
        when(userRoleRepository.existsByUserIdAndRoleCode(1L, "ADMIN")).thenReturn(true);

        assertThatCode(() -> service.requireMember(11L, 1L)).doesNotThrowAnyException();
        verify(memberRepository, never()).findByCourseIdAndUserIdAndStatus(any(), any(), any());
    }

    @Test
    void administratorCannotTeachWithoutExplicitQualificationAndMembership() {
        when(userRoleRepository.existsByUserIdAndRoleCode(1L, "ADMIN")).thenReturn(true);
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(
                new UserProfile(1L, "Administrator", AccountType.PLATFORM)));

        assertThatThrownBy(() -> service.requireTeachingStaff(11L, 1L))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        verify(memberRepository, never()).existsByCourseIdAndUserIdAndRoleInAndStatus(any(), any(), any(), any());
    }

    @Test
    void teacherQualifiedAdministratorStillNeedsCourseMembership() {
        when(userRoleRepository.existsByUserIdAndRoleCode(1L, "ADMIN")).thenReturn(true);
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(
                new UserProfile(1L, "Teacher administrator", AccountType.TEACHER)));
        when(memberRepository.existsByCourseIdAndUserIdAndRoleInAndStatus(
                org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(CourseMemberStatus.ACTIVE)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.requireTeachingStaff(11L, 1L))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }
}
