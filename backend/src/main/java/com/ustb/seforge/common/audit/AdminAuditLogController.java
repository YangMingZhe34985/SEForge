package com.ustb.seforge.common.audit;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read side of the platform audit trail. Guarded by the /api/v1/admin/** ADMIN rule
 * in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController {
    private final AuditQueryService queryService;

    public AdminAuditLogController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiEnvelope<PageResponse<AuditLogView>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiEnvelope.success(queryService.list(page, size));
    }
}
