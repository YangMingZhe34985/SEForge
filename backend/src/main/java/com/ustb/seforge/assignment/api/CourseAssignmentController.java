package com.ustb.seforge.assignment.api;

import com.ustb.seforge.assignment.service.AssignmentService;
import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/assignments")
public class CourseAssignmentController {
    private final AssignmentService assignments;

    public CourseAssignmentController(AssignmentService assignments) {
        this.assignments = assignments;
    }

    @GetMapping
    public ApiEnvelope<PageResponse<AssignmentSummaryView>> list(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiEnvelope.success(assignments.list(courseId, principal.userId(), page, size));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<AssignmentSummaryView>> create(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.success(assignments.create(courseId, principal.userId(), request)));
    }
}
