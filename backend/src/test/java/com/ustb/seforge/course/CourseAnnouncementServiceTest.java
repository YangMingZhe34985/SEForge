package com.ustb.seforge.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.course.api.CreateAnnouncementRequest;
import com.ustb.seforge.course.domain.CourseAnnouncement;
import com.ustb.seforge.course.repository.CourseAnnouncementRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.course.service.CourseAnnouncementService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CourseAnnouncementServiceTest {
    @Test
    void recordsSuccessfulAnnouncementCreationInBusinessTransaction() {
        CourseAnnouncementRepository announcements = mock(CourseAnnouncementRepository.class);
        CourseAccessService access = mock(CourseAccessService.class);
        AuditService audit = mock(AuditService.class);
        CourseAnnouncementService service = new CourseAnnouncementService(announcements, access, audit);
        when(announcements.save(any(CourseAnnouncement.class))).thenAnswer(invocation -> {
            CourseAnnouncement announcement = invocation.getArgument(0);
            ReflectionTestUtils.setField(announcement, "id", 31L);
            return announcement;
        });

        var result = service.create(10L, 7L,
                new CreateAnnouncementRequest("  Week 1  ", "  Welcome  "));

        assertThat(result.title()).isEqualTo("Week 1");
        assertThat(result.content()).isEqualTo("Welcome");
        verify(access).requireTeachingStaff(10L, 7L);
        verify(audit).record(7L, 10L, "COURSE_ANNOUNCEMENT_CREATE",
                "COURSE_ANNOUNCEMENT", 31L, AuditService.SUCCEEDED);
    }
}
