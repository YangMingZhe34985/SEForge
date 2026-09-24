package com.ustb.seforge.course.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.course.service.CourseService;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/courses")
public class AdminCourseController {
    private final CourseService courses;
    private final AuditService audit;

    public AdminCourseController(CourseService courses, AuditService audit) {
        this.courses = courses;
        this.audit = audit;
    }

    @PatchMapping("/{courseId}")
    public ApiEnvelope<CourseDetailsView> update(@PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCourseRequest request) {
        CourseDetailsView course = courses.adminUpdateCourse(courseId, principal.userId(), request);
        audit.record(principal.userId(), courseId, "ADMIN_COURSE_UPDATE", "COURSE", courseId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(course);
    }

    @PutMapping("/{courseId}/owner")
    public ApiEnvelope<CourseDetailsView> transferOwner(@PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TransferCourseOwnerRequest request) {
        CourseDetailsView course = courses.transferOwner(courseId, principal.userId(), request.userId());
        audit.record(principal.userId(), courseId, "ADMIN_COURSE_OWNER_TRANSFER", "COURSE", courseId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(course);
    }
}
