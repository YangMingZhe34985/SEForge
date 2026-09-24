package com.ustb.seforge.course.service;

import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.api.CourseAnnouncementView;
import com.ustb.seforge.course.api.CreateAnnouncementRequest;
import com.ustb.seforge.course.domain.CourseAnnouncement;
import com.ustb.seforge.course.repository.CourseAnnouncementRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseAnnouncementService {
    private final CourseAnnouncementRepository announcements;
    private final CourseAccessService access;
    private final AuditService audit;

    public CourseAnnouncementService(
            CourseAnnouncementRepository announcements, CourseAccessService access,
            AuditService audit) {
        this.announcements = announcements;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseAnnouncementView> list(Long courseId, Long userId, int page, int size) {
        access.requireMember(courseId, userId);
        return PageResponse.from(announcements
                .findAllByCourseIdAndPublishedAtIsNotNullOrderByPublishedAtDesc(courseId,
                        PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .map(CourseAnnouncementView::from));
    }

    @Transactional
    public CourseAnnouncementView create(
            Long courseId, Long userId, CreateAnnouncementRequest request) {
        access.requireTeachingStaff(courseId, userId);
        CourseAnnouncement saved = announcements.save(new CourseAnnouncement(courseId, userId,
                request.title().trim(), request.content().trim()));
        audit.record(userId, courseId, "COURSE_ANNOUNCEMENT_CREATE",
                "COURSE_ANNOUNCEMENT", saved.getId(), AuditService.SUCCEEDED);
        return CourseAnnouncementView.from(saved);
    }

    @Transactional
    public CourseAnnouncementView update(Long courseId, Long announcementId, Long userId,
                                         CreateAnnouncementRequest request) {
        access.requireTeachingStaff(courseId, userId);
        CourseAnnouncement announcement = requirePublished(courseId, announcementId);
        announcement.update(request.title(), request.content());
        audit.record(userId, courseId, "COURSE_ANNOUNCEMENT_UPDATE", "COURSE_ANNOUNCEMENT", announcementId,
                AuditService.SUCCEEDED);
        return CourseAnnouncementView.from(announcement);
    }

    @Transactional
    public void withdraw(Long courseId, Long announcementId, Long userId) {
        access.requireTeachingStaff(courseId, userId);
        requirePublished(courseId, announcementId).withdraw();
        audit.record(userId, courseId, "COURSE_ANNOUNCEMENT_WITHDRAW", "COURSE_ANNOUNCEMENT", announcementId,
                AuditService.SUCCEEDED);
    }

    private CourseAnnouncement requirePublished(Long courseId, Long announcementId) {
        return announcements.findByIdAndCourseId(announcementId, courseId)
                .filter(value -> value.getPublishedAt() != null)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Announcement not found"));
    }
}
