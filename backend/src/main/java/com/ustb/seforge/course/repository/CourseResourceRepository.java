package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.CourseResource;
import com.ustb.seforge.course.domain.ResourceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseResourceRepository extends JpaRepository<CourseResource, Long> {
    List<CourseResource> findAllByCourseIdAndStatusOrderByCreatedAtDesc(Long courseId, ResourceStatus status);
    Optional<CourseResource> findByIdAndCourseIdAndStatus(Long id, Long courseId, ResourceStatus status);
    boolean existsByObjectKey(String objectKey);
    boolean existsByChapterIdAndStatus(Long chapterId, ResourceStatus status);
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(r.sizeBytes),0) from CourseResource r where r.courseId=:course and r.status=com.ustb.seforge.course.domain.ResourceStatus.ACTIVE")
    long sumStoredBytes(@org.springframework.data.repository.query.Param("course") Long course);
}
