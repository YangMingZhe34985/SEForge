package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.course.domain.CourseMember;
import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.domain.CourseMemberStatus;
import com.ustb.seforge.course.repository.CourseMemberRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssignmentAuthorizationServiceTest {
    @Test
    void rejectsStudentFromAnotherClass() {
        CourseAccessService courseAccess = mock(CourseAccessService.class);
        CourseMemberRepository members = mock(CourseMemberRepository.class);
        AssignmentAuthorizationService service = new AssignmentAuthorizationService(courseAccess, members);
        Assignment assignment = new Assignment(10L, 100L, 1L, "A", null, null, null, 1, null);
        CourseMember member = new CourseMember(10L, 200L, 7L, CourseMemberRole.STUDENT);
        when(members.findByCourseIdAndUserIdAndStatus(10L, 7L, CourseMemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.requireStudent(assignment, 7L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("assignment");
    }
}
