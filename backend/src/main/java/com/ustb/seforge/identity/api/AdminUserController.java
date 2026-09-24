package com.ustb.seforge.identity.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.identity.service.IdentityService;
import com.ustb.seforge.identity.service.SessionRevocationService;
import com.ustb.seforge.identity.service.PasswordResetService;
import com.ustb.seforge.identity.service.UserImportService;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import java.io.IOException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.ustb.seforge.identity.security.UserPrincipal;
import com.ustb.seforge.identity.domain.AccountType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final IdentityService identityService;
    private final AuditService auditService;
    private final SessionRevocationService sessions;
    private final PasswordResetService passwordResets;
    private final UserImportService imports;

    public AdminUserController(IdentityService identityService, AuditService auditService,
                               SessionRevocationService sessions, PasswordResetService passwordResets,
                               UserImportService imports) {
        this.identityService = identityService;
        this.auditService = auditService;
        this.sessions = sessions;
        this.passwordResets = passwordResets;
        this.imports = imports;
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
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AccountType accountType,
            @RequestParam(required = false) String search) {
        return ApiEnvelope.success(identityService.listUsers(page, size, accountType, search));
    }

    @GetMapping("/{userId}")
    public ApiEnvelope<UserView> get(@PathVariable Long userId) {
        return ApiEnvelope.success(identityService.getUser(userId));
    }

    @PutMapping("/{userId}/profile")
    public ApiEnvelope<UserView> updateProfile(@PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        UserView user = identityService.updateProfile(userId, request);
        sessions.revokePrincipal(user.username());
        auditService.record(principal.userId(), null, "ADMIN_USER_PROFILE", "USER", userId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(user);
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

    @PostMapping("/{userId}/password-reset")
    public ApiEnvelope<PasswordResetTokenView> issuePasswordReset(@PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        PasswordResetTokenView token = passwordResets.issue(userId);
        auditService.record(principal.userId(), null, "ADMIN_PASSWORD_RESET_ISSUE", "USER", userId,
                AuditService.SUCCEEDED);
        return ApiEnvelope.success(token);
    }

    @PostMapping(path = "/import/preview", consumes = "multipart/form-data")
    public ApiEnvelope<UserImportPreviewView> previewImport(@RequestPart("file") MultipartFile file) {
        return ApiEnvelope.success(imports.preview(readCsv(file)));
    }

    @PostMapping(path = "/import/confirm", consumes = "multipart/form-data")
    public ApiEnvelope<UserImportResultView> confirmImport(@AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file, @RequestParam String digest) {
        UserImportResultView result = imports.confirm(readCsv(file), digest);
        auditService.record(principal.userId(), null, "ADMIN_USER_IMPORT", "USER", null,
                result.failed() == 0 ? AuditService.SUCCEEDED : AuditService.FAILED);
        return ApiEnvelope.success(result);
    }

    private byte[] readCsv(MultipartFile file) {
        if (file.getSize() > 1024 * 1024) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "CSV exceeds 1 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Cannot read CSV");
        }
    }
}
