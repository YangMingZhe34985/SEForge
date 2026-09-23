package com.ustb.seforge.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.analytics.api.DashboardView;
import com.ustb.seforge.analytics.domain.AnalyticsSnapshot;
import com.ustb.seforge.analytics.repository.AnalyticsSnapshotRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.job.api.AsyncJobView;
import com.ustb.seforge.job.domain.JobKind;
import com.ustb.seforge.job.service.AsyncJobService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AnalyticsServiceTest {
    private AnalyticsSnapshotRepository snapshots;
    private DashboardQueryService dashboard;
    private AnalyticsSourceCursorService sourceCursors;
    private CourseAccessService access;
    private AsyncJobService jobs;
    private ObjectMapper objectMapper;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        snapshots = mock(AnalyticsSnapshotRepository.class);
        dashboard = mock(DashboardQueryService.class);
        sourceCursors = mock(AnalyticsSourceCursorService.class);
        access = mock(CourseAccessService.class);
        jobs = mock(AsyncJobService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AnalyticsService(
                snapshots, dashboard, sourceCursors, access, jobs, objectMapper);
    }

    @Test
    void snapshotRequestValidatesOnlyTheScopeAndLeavesAggregationToTheWorker() {
        AsyncJobView queued = mock(AsyncJobView.class);
        when(jobs.submit(eq(JobKind.ANALYTICS_SNAPSHOT), eq(7L), eq(3L),
                any(AnalyticsService.SnapshotRequest.class), eq("request-1"))).thenReturn(queued);

        AsyncJobView result = service.requestSnapshot(3L, 9L, 7L, "request-1");

        assertThat(result).isSameAs(queued);
        verify(access).requireTeachingStaff(3L, 7L);
        verify(dashboard).validateScope(3L, 9L);
        verify(dashboard, never()).aggregate(any(), any());
    }

    @Test
    void dashboardRequestReadsMaterializedPayloadWithoutScanningSourcesOrAggregating() throws Exception {
        Instant generatedAt = Instant.parse("2026-09-22T01:00:00Z");
        DashboardView materialized = view(generatedAt, 12, new BigDecimal("81.25"));
        AnalyticsSnapshot snapshot = snapshot(41L, "cursor-a", null, generatedAt, materialized);
        when(snapshots.findFirstByCourseIdAndClassIdIsNullAndMetricTypeOrderByGeneratedAtDesc(
                3L, AnalyticsService.DASHBOARD_METRIC)).thenReturn(Optional.of(snapshot));

        DashboardView result = service.dashboard(3L, null, 7L);

        assertThat(result).isEqualTo(materialized);
        verify(access).requireTeachingStaff(3L, 7L);
        verify(dashboard).validateScope(3L, null);
        verify(dashboard, never()).aggregate(any(), any());
        verify(sourceCursors, never()).current(any());
    }

    @Test
    void unchangedSourceCursorReusesLatestMaterialization() throws Exception {
        Instant generatedAt = Instant.parse("2026-09-22T01:00:00Z");
        AnalyticsSnapshot previous = snapshot(
                41L, "cursor-a", null, generatedAt,
                view(generatedAt, 12, new BigDecimal("70")));
        when(snapshots.findFirstByCourseIdAndClassIdIsNullAndMetricTypeOrderByGeneratedAtDesc(
                3L, AnalyticsService.DASHBOARD_METRIC)).thenReturn(Optional.of(previous));
        when(sourceCursors.current(3L)).thenReturn("cursor-a");

        String result = service.generate(3L, null, Instant.parse("2026-09-22T02:00:00Z"));

        assertThat(objectMapper.readTree(result).path("status").asText()).isEqualTo("UNCHANGED");
        assertThat(objectMapper.readTree(result).path("snapshotId").asLong()).isEqualTo(41L);
        verify(dashboard, never()).aggregate(any(), any());
        verify(snapshots, never()).save(any());
    }

    @Test
    void changedCursorRecomputesAuthoritativeScopeAndAdvancesPeriod() throws Exception {
        Instant previousEnd = Instant.parse("2026-09-22T01:00:00Z");
        Instant refreshedAt = Instant.parse("2026-09-22T02:00:00Z");
        AnalyticsSnapshot previous = snapshot(
                41L, "cursor-old", null, previousEnd,
                view(previousEnd, 10, new BigDecimal("65")));
        DashboardView corrected = view(refreshedAt, 9, new BigDecimal("88.50"));
        when(snapshots.findFirstByCourseIdAndClassIdIsNullAndMetricTypeOrderByGeneratedAtDesc(
                3L, AnalyticsService.DASHBOARD_METRIC)).thenReturn(Optional.of(previous));
        when(sourceCursors.current(3L)).thenReturn("cursor-new");
        when(dashboard.aggregate(3L, null)).thenReturn(corrected);
        when(snapshots.save(any(AnalyticsSnapshot.class))).thenAnswer(invocation -> {
            AnalyticsSnapshot saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        String result = service.generate(3L, null, Instant.parse("2026-09-22T01:30:00Z"));

        assertThat(objectMapper.readTree(result).path("snapshotId").asLong()).isEqualTo(42L);
        ArgumentCaptor<AnalyticsSnapshot> captor = ArgumentCaptor.forClass(AnalyticsSnapshot.class);
        verify(snapshots).save(captor.capture());
        AnalyticsSnapshot saved = captor.getValue();
        assertThat(saved.getSourceCursor()).isEqualTo("cursor-new");
        assertThat(saved.getPeriodStart()).isEqualTo(previousEnd);
        assertThat(saved.getPeriodEnd()).isEqualTo(refreshedAt);
        DashboardView payload = objectMapper.readValue(saved.getPayloadJson(), DashboardView.class);
        assertThat(payload.overview().students()).isEqualTo(9);
        assertThat(payload.overview().averageFinalScore()).isEqualByComparingTo("88.50");
    }

    private AnalyticsSnapshot snapshot(Long id, String cursor, Instant periodStart,
                                       Instant periodEnd, DashboardView view) throws Exception {
        AnalyticsSnapshot snapshot = new AnalyticsSnapshot(3L, null,
                AnalyticsService.DASHBOARD_METRIC, cursor, periodStart, periodEnd,
                objectMapper.writeValueAsString(view), periodEnd);
        ReflectionTestUtils.setField(snapshot, "id", id);
        return snapshot;
    }

    private DashboardView view(Instant generatedAt, long students, BigDecimal average) {
        return new DashboardView(3L, null, generatedAt,
                new DashboardView.Overview(students, 2, 18, 16,
                        new BigDecimal("88.89"), average),
                List.of(), List.of(), List.of(),
                new DashboardView.TutorMetrics(0, 0, Map.of()),
                new DashboardView.QaFeedbackMetrics(0, 0, BigDecimal.ZERO),
                new DashboardView.ErrorMetrics(0, 0));
    }
}
