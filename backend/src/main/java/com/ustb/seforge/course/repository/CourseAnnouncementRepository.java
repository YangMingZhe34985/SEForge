package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.CourseAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseAnnouncementRepository extends JpaRepository<CourseAnnouncement, Long> {
    Page<CourseAnnouncement> findAllByCourseIdAndPublishedAtIsNotNullOrderByPublishedAtDesc(
            Long courseId, Pageable pageable);
}
