package com.ustb.seforge.identity.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.identity.service.IdentityService;
import com.ustb.seforge.identity.service.SessionRevocationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.ustb.seforge.identity.security.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final IdentityService identityService;
    private final AuditService auditService;
    private final SessionRevocationService sessions;

    public AdminUserController(IdentityService identityService, AuditService auditService,
                               SessionRevocationService sessions) {
        this.identityService = identityService;
        this.auditService = auditService;
        this.sessions = sessions;
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<UserView>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateUserRequest request) {
        UserView user = identityService.createUser(request);
        auditService.record(principal.userId(), null, "ADMIN_USER_CREATE", "USER", user.id(),
                AuditService.SUCCEEDED);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(user));
    }

    @GetMapping
    public ApiEnvelope<PageResponse<UserView>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiEnvelope.success(identityService.listUsers(page, size));
    }

    @PatchMapping("/{userId}")
    public ApiEnvelope<UserView> updateStatus(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        UserView user = identityService.updateEnabled(userId, principal.userId(), request.enabled());
        sessions.revokePrincipal(user.username());
        auditService.record(principal.userId(), null, "ADMIN_USER_STATUS", "USER", userId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(user);
    }

    @PutMapping("/{userId}/roles")
    public ApiEnvelope<UserView> updateRoles(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateUserRolesRequest request) {
        UserView user = identityService.updateRoles(userId, principal.userId(), request.roles());
        sessions.revokePrincipal(user.username());
        auditService.record(principal.userId(), null, "ADMIN_USER_ROLES", "USER", userId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(user);
    }
}
