package com.ustb.seforge.review.repository;

import com.ustb.seforge.review.domain.ReviewReport;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {
    Optional<ReviewReport> findByReviewJobId(Long reviewJobId);
    Optional<ReviewReport> findByIdAndCourseId(Long id, Long courseId);
    Page<ReviewReport> findAllByCourseIdOrderByGeneratedAtDesc(Long courseId, Pageable pageable);
}
