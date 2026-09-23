package com.ustb.seforge.assignment.repository;

import com.ustb.seforge.assignment.domain.Assignment;
import com.ustb.seforge.assignment.domain.AssignmentStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    Optional<Assignment> findByIdAndCourseId(Long id, Long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id")
    Optional<Assignment> findByIdForUpdate(@Param("id") Long id);

    Page<Assignment> findAllByCourseIdOrderByCreatedAtDesc(Long courseId, Pageable pageable);

    @Query("""
            select a from Assignment a
            where a.courseId = :courseId
              and a.status in :statuses
              and (a.classId is null or a.classId = :classId)
              and (a.availableAt is null or a.availableAt <= CURRENT_TIMESTAMP)
            order by a.dueAt asc, a.createdAt desc
            """)
    Page<Assignment> findVisibleForStudent(@Param("courseId") Long courseId,
                                           @Param("classId") Long classId,
                                           @Param("statuses") Collection<AssignmentStatus> statuses,
                                           Pageable pageable);
}
