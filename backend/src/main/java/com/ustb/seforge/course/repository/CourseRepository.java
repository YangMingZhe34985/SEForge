package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.Course;
import com.ustb.seforge.course.domain.CourseMemberStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {
    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findForUpdate(@Param("id") Long id);

    @Query("select c from Course c where exists (select m.id from CourseMember m "
            + "where m.courseId = c.id and m.userId = :userId and m.status = :status)")
    Page<Course> findVisibleToUser(
            @Param("userId") Long userId,
            @Param("status") CourseMemberStatus status,
            Pageable pageable);
}
