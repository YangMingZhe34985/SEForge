package com.ustb.seforge.job.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.identity.security.UserPrincipal;
import com.ustb.seforge.job.service.AsyncJobService;
import com.ustb.seforge.job.service.JobEventBroker;
import com.ustb.seforge.course.service.CourseAccessService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/jobs")
public class AsyncJobController {
    private final AsyncJobService jobs;
    private final JobEventBroker events;
    private final CourseAccessService courseAccess;

    public AsyncJobController(AsyncJobService jobs, JobEventBroker events,
                              CourseAccessService courseAccess) {
        this.jobs = jobs;
        this.events = events;
        this.courseAccess = courseAccess;
    }

    @GetMapping("/{jobId}")
    public ApiEnvelope<AsyncJobView> get(@PathVariable Long jobId,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(requireVisible(jobId, principal));
    }

    @DeleteMapping("/{jobId}")
    public ApiEnvelope<AsyncJobView> cancel(@PathVariable Long jobId,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        AsyncJobView job = jobs.require(jobId);
        if (job.courseId() == null) return ApiEnvelope.success(jobs.cancel(jobId, principal.userId()));
        courseAccess.requireTeachingStaff(job.courseId(), principal.userId());
        return ApiEnvelope.success(jobs.cancelForCourse(jobId, job.courseId()));
    }

    @GetMapping(value = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long jobId,
                             @AuthenticationPrincipal UserPrincipal principal) {
        return events.subscribe(requireVisible(jobId, principal));
    }

    private AsyncJobView requireVisible(Long jobId, UserPrincipal principal) {
        AsyncJobView job = jobs.require(jobId);
        if (job.courseId() == null) return jobs.requireOwned(jobId, principal.userId());
        if (!jobs.isOwnedBy(jobId, principal.userId())) {
            courseAccess.requireTeachingStaff(job.courseId(), principal.userId());
        }
        return job;
    }
}
