package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.Semester;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    boolean existsByCodeIgnoreCase(String code);
    List<Semester> findAllByOrderByStartsOnDesc();
}
