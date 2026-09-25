package com.ustb.seforge.review.repository;

import com.ustb.seforge.review.domain.ReviewJob;
import com.ustb.seforge.review.domain.ReviewType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewJobRepository extends JpaRepository<ReviewJob, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from ReviewJob r where r.id = :id and r.courseId = :courseId")
    Optional<ReviewJob> findForRetry(@org.springframework.data.repository.query.Param("id") Long id,
                                   @org.springframework.data.repository.query.Param("courseId") Long courseId);
    Optional<ReviewJob> findByIdAndCourseId(Long id, Long courseId);
    Optional<ReviewJob> findByAsyncJobId(Long asyncJobId);
    Page<ReviewJob> findAllByCourseIdOrderByCreatedAtDesc(Long courseId, Pageable pageable);
    Page<ReviewJob> findAllByCourseIdAndReviewTypeOrderByCreatedAtDesc(
            Long courseId, ReviewType reviewType, Pageable pageable);
}
