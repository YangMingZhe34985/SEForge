package com.ustb.seforge.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.api.CreateCourseRequest;
import com.ustb.seforge.course.api.CreateCourseResourceRequest;
import com.ustb.seforge.course.domain.Course;
import com.ustb.seforge.course.domain.CourseClass;
import com.ustb.seforge.course.domain.CourseInvite;
import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.domain.CourseMemberStatus;
import com.ustb.seforge.course.domain.ResourceType;
import com.ustb.seforge.course.repository.CourseChapterRepository;
import com.ustb.seforge.course.repository.CourseClassRepository;
import com.ustb.seforge.course.repository.CourseInviteRepository;
import com.ustb.seforge.course.repository.CourseMemberRepository;
import com.ustb.seforge.course.repository.CourseRepository;
import com.ustb.seforge.course.repository.CourseResourceRepository;
import com.ustb.seforge.course.repository.KnowledgePointRepository;
import com.ustb.seforge.course.repository.SemesterRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.course.service.CourseService;
import com.ustb.seforge.identity.service.IdentityService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {
    @Mock SemesterRepository semesterRepository;
    @Mock CourseRepository courseRepository;
    @Mock CourseClassRepository classRepository;
    @Mock CourseMemberRepository memberRepository;
    @Mock CourseInviteRepository inviteRepository;
    @Mock CourseChapterRepository chapterRepository;
    @Mock KnowledgePointRepository knowledgePointRepository;
    @Mock CourseResourceRepository resourceRepository;
    @Mock CourseAccessService accessService;
    @Mock IdentityService identityService;

    private CourseService service;

    @BeforeEach
    void setUp() {
        service = new CourseService(
                semesterRepository, courseRepository, classRepository, memberRepository, inviteRepository,
                chapterRepository, knowledgePointRepository, resourceRepository, accessService, identityService);
    }

    @Test
    void studentAccountCannotCreateCourse() {
        when(identityService.isTeacher(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.createCourse(
                9L, new CreateCourseRequest("SE101", "Software Engineering", null, 3L)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void expiredInviteCannotBeUsed() {
        CourseInvite invite = new CourseInvite(
                "ABCDEFGH", 2L, null, CourseMemberRole.STUDENT, 10, Instant.now().minusSeconds(1), 1L);
        when(inviteRepository.findByCodeForUpdate("ABCDEFGH")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> service.joinCourse(9L, "abcdefgh"))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVITE_EXPIRED));
    }

    @Test
    void classInviteLocksClassAndRejectsJoinWhenCapacityIsFull() {
        CourseInvite invite = new CourseInvite(
                "ABCDEFGH", 2L, 5L, CourseMemberRole.STUDENT, 10, null, 1L);
        Course course = new Course("SE101", "Software Engineering", null, 3L, 1L);
        CourseClass courseClass = new CourseClass(2L, "A", "Class A", 1, true);
        ReflectionTestUtils.setField(course, "id", 2L);
        ReflectionTestUtils.setField(courseClass, "id", 5L);
        when(inviteRepository.findByCodeForUpdate("ABCDEFGH")).thenReturn(Optional.of(invite));
        when(courseRepository.findById(2L)).thenReturn(Optional.of(course));
        when(memberRepository.findByCourseIdAndUserId(2L, 9L)).thenReturn(Optional.empty());
        when(classRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(courseClass));
        when(memberRepository.countByCourseIdAndClassIdAndStatus(
                2L, 5L, CourseMemberStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.joinCourse(9L, "abcdefgh"))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception).hasMessageContaining("capacity");
                });

        InOrder capacityCheck = inOrder(classRepository, memberRepository);
        capacityCheck.verify(classRepository).findByIdForUpdate(5L);
        capacityCheck.verify(memberRepository).countByCourseIdAndClassIdAndStatus(
                2L, 5L, CourseMemberStatus.ACTIVE);
        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resourceObjectKeyMustStayWithinCourseNamespace() {
        when(courseRepository.findById(22L))
                .thenReturn(Optional.of(new Course("SE101", "Software Engineering", null, 1L, 2L)));
        CreateCourseResourceRequest request = new CreateCourseResourceRequest(
                null, "Lecture", null, ResourceType.DOCUMENT,
                "courses/another-course/../private.pdf", "application/pdf", 100L);

        assertThatThrownBy(() -> service.createResource(22L, 2L, request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
