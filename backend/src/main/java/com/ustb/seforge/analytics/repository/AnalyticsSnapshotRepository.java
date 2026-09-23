package com.ustb.seforge.analytics.repository;

import com.ustb.seforge.analytics.domain.AnalyticsSnapshot;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsSnapshotRepository extends JpaRepository<AnalyticsSnapshot, Long> {
    Page<AnalyticsSnapshot> findAllByCourseIdAndClassIdOrderByGeneratedAtDesc(
            Long courseId, Long classId, Pageable pageable);

    Page<AnalyticsSnapshot> findAllByCourseIdAndClassIdIsNullOrderByGeneratedAtDesc(
            Long courseId, Pageable pageable);

    Optional<AnalyticsSnapshot> findFirstByCourseIdAndClassIdAndMetricTypeOrderByGeneratedAtDesc(
            Long courseId, Long classId, String metricType);

    Optional<AnalyticsSnapshot> findFirstByCourseIdAndClassIdIsNullAndMetricTypeOrderByGeneratedAtDesc(
            Long courseId, String metricType);

}
