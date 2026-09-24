package com.ustb.seforge.course.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.course.service.AdminOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/overview")
public class AdminOverviewController {
    private final AdminOverviewService overview;

    public AdminOverviewController(AdminOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping
    public ApiEnvelope<AdminOverviewView> get() {
        return ApiEnvelope.success(overview.current());
    }
}
