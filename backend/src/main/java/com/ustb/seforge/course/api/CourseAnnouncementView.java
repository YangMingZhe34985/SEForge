package com.ustb.seforge.course.api;

import com.ustb.seforge.course.domain.CourseAnnouncement;
import java.time.Instant;

public record CourseAnnouncementView(Long id, Long courseId, Long authorId, String title,
                                     String content, Instant publishedAt) {
    public static CourseAnnouncementView from(CourseAnnouncement value) {
        return new CourseAnnouncementView(value.getId(), value.getCourseId(), value.getAuthorId(),
                value.getTitle(), value.getContent(), value.getPublishedAt());
    }
}
