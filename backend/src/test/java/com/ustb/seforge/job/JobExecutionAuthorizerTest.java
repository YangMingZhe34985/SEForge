package com.ustb.seforge.job;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.identity.domain.User;
import com.ustb.seforge.identity.repository.UserRepository;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.service.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JobExecutionAuthorizerTest {
    @Test void rechecksDisabledAccountAndCoursePermissionOnEveryAttempt() {
        var users = mock(UserRepository.class); var courses = mock(CourseAccessService.class);
        var user = new User("test@example.invalid", "test", "unused");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        var authorization = new JobExecutionAuthorizer(users, courses);
        var job = new JobSnapshot(1L, JobKind.REVIEW_DOCUMENT, 7L, 9L, "{}", 1, "worker");
        authorization.authorize(job);
        user.setEnabled(false);
        assertThatThrownBy(() -> authorization.authorize(job)).hasMessageContaining("no longer authorized");
        user.setEnabled(true);
        doThrow(new SecurityException("revoked")).when(courses).requireTeachingStaff(9L, 7L);
        assertThatThrownBy(() -> authorization.authorize(job)).isInstanceOf(SecurityException.class);
        verify(courses, times(2)).requireTeachingStaff(9L, 7L);
    }
}
