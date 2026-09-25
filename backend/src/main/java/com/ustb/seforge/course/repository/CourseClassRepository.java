package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.CourseClass;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseClassRepository extends JpaRepository<CourseClass, Long> {
    List<CourseClass> findAllByCourseIdOrderByNameAsc(Long courseId);
    boolean existsByCourseIdAndCodeIgnoreCase(Long courseId, String code);
    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourseClass c where c.id = :id")
    Optional<CourseClass> findByIdForUpdate(@Param("id") Long id);
}
