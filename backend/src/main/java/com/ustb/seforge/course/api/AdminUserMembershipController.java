package com.ustb.seforge.course.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.course.service.CourseService;
import com.ustb.seforge.identity.security.UserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserMembershipController {
    private final CourseService courses;

    public AdminUserMembershipController(CourseService courses) { this.courses = courses; }

    @GetMapping("/{userId}/memberships")
    public ApiEnvelope<List<AdminUserMembershipView>> list(@PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.success(courses.adminMemberships(userId, principal.userId()));
    }
}
