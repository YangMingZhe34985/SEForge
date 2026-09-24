package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.CourseChapter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseChapterRepository extends JpaRepository<CourseChapter, Long> {
    List<CourseChapter> findAllByCourseIdOrderBySortOrderAscIdAsc(Long courseId);
    boolean existsByParentId(Long parentId);
}
