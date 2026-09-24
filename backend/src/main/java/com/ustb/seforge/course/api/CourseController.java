package com.ustb.seforge.course.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.course.service.CourseService;
import com.ustb.seforge.course.domain.CourseStatus;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {
    private final CourseService courseService;
    private final AuditService auditService;

    public CourseController(CourseService courseService, AuditService auditService) {
        this.courseService = courseService;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiEnvelope<PageResponse<CourseSummaryView>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) Long semesterId) {
        return ApiEnvelope.success(courseService.listCourses(principal.userId(), page, size, search, status, semesterId));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<CourseDetailsView>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCourseRequest request) {
        CourseDetailsView course = courseService.createCourse(principal.userId(), request);
        auditService.record(principal.userId(), course.id(), "COURSE_CREATE", "COURSE", course.id(),
                AuditService.SUCCEEDED);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(course));
    }

    @PostMapping("/join")
    public ApiEnvelope<CourseDetailsView> join(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody JoinCourseRequest request) {
        CourseDetailsView course = courseService.joinCourse(principal.userId(), request.inviteCode());
        auditService.record(principal.userId(), course.id(), "COURSE_JOIN", "COURSE", course.id(),
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(course);
    }

    @GetMapping("/{courseId}")
    public ApiEnvelope<CourseDetailsView> get(
            @PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(courseService.getCourse(courseId, principal.userId()));
    }

    @PatchMapping("/{courseId}")
    public ApiEnvelope<CourseDetailsView> update(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCourseRequest request) {
        CourseDetailsView course = courseService.updateCourse(courseId, principal.userId(), request);
        auditService.record(principal.userId(), courseId, "COURSE_UPDATE", "COURSE", courseId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(course);
    }

    @DeleteMapping("/{courseId}/members/{userId}")
    public ApiEnvelope<Void> removeMember(
            @PathVariable Long courseId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        courseService.removeMember(courseId, userId, principal.userId());
        auditService.record(principal.userId(), courseId, "COURSE_MEMBER_REMOVE", "USER", userId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success("Course member removed", null);
    }
}
