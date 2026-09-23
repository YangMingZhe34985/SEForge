package com.ustb.seforge.course.repository;

import com.ustb.seforge.course.domain.CourseInvite;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseInviteRepository extends JpaRepository<CourseInvite, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from CourseInvite invite where upper(invite.code) = upper(:code)")
    Optional<CourseInvite> findByCodeForUpdate(@Param("code") String code);

    List<CourseInvite> findAllByCourseIdOrderByCreatedAtDesc(Long courseId);
    boolean existsByCodeIgnoreCase(String code);
}
