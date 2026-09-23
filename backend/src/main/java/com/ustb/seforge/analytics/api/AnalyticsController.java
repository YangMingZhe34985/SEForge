package com.ustb.seforge.analytics.api;

import com.ustb.seforge.analytics.service.AnalyticsService;
import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.identity.security.UserPrincipal;
import com.ustb.seforge.job.api.AsyncJobView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/analytics")
public class AnalyticsController {
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/dashboard")
    public ApiEnvelope<DashboardView> dashboard(
            @PathVariable Long courseId,
            @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(analytics.dashboard(courseId, classId, principal.userId()));
    }

    @PostMapping("/snapshots")
    public ApiEnvelope<AsyncJobView> snapshot(
            @PathVariable Long courseId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(analytics.requestSnapshot(
                courseId, classId, principal.userId(), idempotencyKey));
    }

    @GetMapping("/snapshots")
    public ApiEnvelope<PageResponse<AnalyticsSnapshotView>> snapshots(
            @PathVariable Long courseId,
            @RequestParam(required = false) Long classId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(analytics.list(
                courseId, classId, principal.userId(), page, size));
    }
}
