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

    private CourseAccessService service;

    @BeforeEach
    void setUp() {
        service = new CourseAccessService(memberRepository, userRoleRepository);
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
}
