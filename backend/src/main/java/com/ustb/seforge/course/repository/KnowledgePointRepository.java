package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.KnowledgePoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {
    List<KnowledgePoint> findAllByCourseIdOrderBySortOrderAscIdAsc(Long courseId);

    boolean existsByIdAndCourseId(Long id, Long courseId);
    boolean existsByChapterId(Long chapterId);
}
