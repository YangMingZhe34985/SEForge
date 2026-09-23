package com.ustb.seforge.review.repository;

import com.ustb.seforge.review.domain.ReviewJob;
import com.ustb.seforge.review.domain.ReviewType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewJobRepository extends JpaRepository<ReviewJob, Long> {
    Optional<ReviewJob> findByIdAndCourseId(Long id, Long courseId);
    Optional<ReviewJob> findByAsyncJobId(Long asyncJobId);
    Page<ReviewJob> findAllByCourseIdOrderByCreatedAtDesc(Long courseId, Pageable pageable);
    Page<ReviewJob> findAllByCourseIdAndReviewTypeOrderByCreatedAtDesc(
            Long courseId, ReviewType reviewType, Pageable pageable);
}
