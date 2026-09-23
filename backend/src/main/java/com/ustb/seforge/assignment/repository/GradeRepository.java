package com.ustb.seforge.assignment.repository;

import com.ustb.seforge.assignment.domain.Grade;
import com.ustb.seforge.assignment.domain.GradeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    Optional<Grade> findBySubmissionId(Long submissionId);
    List<Grade> findAllByCourseIdAndStatus(Long courseId, GradeStatus status);
    List<Grade> findAllByStudentIdAndCourseIdOrderByUpdatedAtDesc(Long studentId, Long courseId);
    Page<Grade> findAllByCourseIdOrderByUpdatedAtDesc(Long courseId, Pageable pageable);
    Page<Grade> findAllByStudentIdAndCourseIdAndStatusOrderByUpdatedAtDesc(
            Long studentId, Long courseId, GradeStatus status, Pageable pageable);
}
