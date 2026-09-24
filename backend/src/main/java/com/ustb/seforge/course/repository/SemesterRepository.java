package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.Semester;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    boolean existsByStatus(com.ustb.seforge.course.domain.SemesterStatus status);
    boolean existsByStatusAndIdNot(com.ustb.seforge.course.domain.SemesterStatus status, Long id);
    boolean existsByCodeIgnoreCase(String code);
    List<Semester> findAllByOrderByStartsOnDesc();
    java.util.Optional<Semester> findFirstByStatusOrderByStartsOnDesc(
            com.ustb.seforge.course.domain.SemesterStatus status);
}
