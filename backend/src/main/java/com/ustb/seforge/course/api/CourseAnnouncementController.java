package com.ustb.seforge.course.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.course.service.CourseAnnouncementService;
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
@RequestMapping("/api/v1/courses/{courseId}/announcements")
public class CourseAnnouncementController {
    private final CourseAnnouncementService announcements;

    public CourseAnnouncementController(CourseAnnouncementService announcements) {
        this.announcements = announcements;
    }

    @GetMapping
    public ApiEnvelope<PageResponse<CourseAnnouncementView>> list(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(announcements.list(
                courseId, principal.userId(), page, size));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<CourseAnnouncementView>> create(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateAnnouncementRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.success(announcements.create(
                        courseId, principal.userId(), request)));
    }
}
