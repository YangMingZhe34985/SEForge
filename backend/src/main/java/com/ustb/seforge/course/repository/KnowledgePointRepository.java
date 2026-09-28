package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.KnowledgePoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {
    List<KnowledgePoint> findAllByCourseIdOrderBySortOrderAscIdAsc(Long courseId);

    boolean existsByIdAndCourseId(Long id, Long courseId);
    boolean existsByChapterId(Long chapterId);
    @org.springframework.data.jpa.repository.Query("select count(q) from AssignmentQuestion q where q.knowledgePointId=:point")
    long assignmentReferences(@org.springframework.data.repository.query.Param("point") Long point);
    @org.springframework.data.jpa.repository.Query("select q.config from AssignmentQuestion q where q.courseId=:course")
    List<String> assignmentConfigs(@org.springframework.data.repository.query.Param("course") Long course);
}
