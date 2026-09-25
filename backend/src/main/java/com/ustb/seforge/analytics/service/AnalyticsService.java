package com.ustb.seforge.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.analytics.api.AnalyticsSnapshotView;
import com.ustb.seforge.analytics.api.DashboardView;
import com.ustb.seforge.analytics.domain.AnalyticsSnapshot;
import com.ustb.seforge.analytics.repository.AnalyticsSnapshotRepository;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.job.api.AsyncJobView;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.service.AsyncJobService;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    public static final String DASHBOARD_METRIC = "DASHBOARD_V1";

    private final AnalyticsSnapshotRepository snapshots;
    private final DashboardQueryService dashboard;
    private final AnalyticsProjectionService projection;
    private final CourseAccessService courseAccess;
    private final AsyncJobService jobs;
    private final ObjectMapper objectMapper;

    public AnalyticsService(AnalyticsSnapshotRepository snapshots, DashboardQueryService dashboard,
                            AnalyticsProjectionService projection,
                            CourseAccessService courseAccess, AsyncJobService jobs,
                            ObjectMapper objectMapper) {
        this.snapshots = snapshots;
        this.dashboard = dashboard;
        this.projection = projection;
        this.courseAccess = courseAccess;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AsyncJobView requestSnapshot(Long courseId, Long classId, Long userId,
                                        String idempotencyKey) {
        courseAccess.requireTeachingStaff(courseId, userId);
        dashboard.validateScope(courseId, classId);
        return jobs.submit(JobKind.ANALYTICS_SNAPSHOT, userId, courseId,
                new SnapshotRequest(classId, Instant.now()), idempotencyKey == null ? null
                        : courseId + ":" + classId + ":" + idempotencyKey);
    }

    @Transactional
    public String generate(Long courseId, Long classId, Instant requestedAt) {
        String sourceCursor = projection.refresh(courseId);
        dashboard.validateScope(courseId, classId);
        AnalyticsSnapshot previous = latest(courseId, classId).orElse(null);
        if (previous != null && sourceCursor.equals(previous.getSourceCursor())) {
            return json(Map.of("snapshotId", previous.getId(), "status", "UNCHANGED"));
        }
        DashboardView view = projection.view(courseId, classId);
        Instant generatedAt = view.generatedAt();
        Instant periodStart = previous == null ? null : previous.getPeriodEnd();
        AnalyticsSnapshot snapshot = snapshots.save(new AnalyticsSnapshot(courseId, classId,
                DASHBOARD_METRIC, sourceCursor, periodStart, generatedAt, json(view), generatedAt));
        return json(Map.of("snapshotId", snapshot.getId(), "status", "COMPLETED"));
    }

    /**
     * Reads the latest materialization only. Refresh scans and aggregation are worker-only work.
     */
    @Transactional(readOnly = true)
    public DashboardView dashboard(Long courseId, Long classId, Long userId) {
        courseAccess.requireTeachingStaff(courseId, userId);
        dashboard.validateScope(courseId, classId);
        AnalyticsSnapshot snapshot = latest(courseId, classId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.RESOURCE_NOT_FOUND, "Dashboard snapshot has not been generated"));
        try {
            return objectMapper.readValue(snapshot.getPayloadJson(), DashboardView.class);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                    "Stored analytics snapshot could not be read");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AnalyticsSnapshotView> list(Long courseId, Long classId, Long userId,
                                                    int page, int size) {
        courseAccess.requireTeachingStaff(courseId, userId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        dashboard.validateScope(courseId, classId);
        Page<AnalyticsSnapshot> source = classId == null
                ? snapshots.findAllByCourseIdAndClassIdIsNullOrderByGeneratedAtDesc(courseId, pageable)
                : snapshots.findAllByCourseIdAndClassIdOrderByGeneratedAtDesc(courseId, classId, pageable);
        return PageResponse.from(source.map(item -> AnalyticsSnapshotView.from(item, objectMapper)));
    }

    private java.util.Optional<AnalyticsSnapshot> latest(Long courseId, Long classId) {
        return classId == null
                ? snapshots.findFirstByCourseIdAndClassIdIsNullAndMetricTypeOrderByGeneratedAtDesc(
                        courseId, DASHBOARD_METRIC)
                : snapshots.findFirstByCourseIdAndClassIdAndMetricTypeOrderByGeneratedAtDesc(
                        courseId, classId, DASHBOARD_METRIC);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Analytics payload could not be serialized");
        }
    }

    public record SnapshotRequest(Long classId, Instant requestedAt) {
    }
}
