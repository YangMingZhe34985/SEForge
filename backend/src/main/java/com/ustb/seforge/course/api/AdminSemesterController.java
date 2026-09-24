package com.ustb.seforge.course.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.course.service.CourseService;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/semesters")
public class AdminSemesterController {
    private final CourseService courseService;
    private final AuditService auditService;

    public AdminSemesterController(CourseService courseService, AuditService auditService) {
        this.courseService = courseService;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<SemesterView>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSemesterRequest request) {
        SemesterView semester = courseService.createSemester(request);
        auditService.record(principal.userId(), null, "SEMESTER_CREATE", "SEMESTER", semester.id(),
                AuditService.SUCCEEDED);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(semester));
    }

    @PutMapping("/{semesterId}")
    public ApiEnvelope<SemesterView> update(@PathVariable Long semesterId,
                                            @AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody UpdateSemesterRequest request) {
        SemesterView semester = courseService.updateSemester(semesterId, request);
        auditService.record(principal.userId(), null, "SEMESTER_UPDATE", "SEMESTER", semesterId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(semester);
    }
}
