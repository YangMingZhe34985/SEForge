package com.ustb.seforge.identity.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.identity.security.UserPrincipal;
import com.ustb.seforge.identity.service.IdentityService;
import com.ustb.seforge.identity.service.LoginAttemptService;
import com.ustb.seforge.identity.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityService identityService;
    private final LoginAttemptService loginAttemptService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AuditService auditService;
    private final PasswordResetService passwordResets;

    public AuthController(
            IdentityService identityService,
            LoginAttemptService loginAttemptService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            AuditService auditService, PasswordResetService passwordResets) {
        this.identityService = identityService;
        this.loginAttemptService = loginAttemptService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.auditService = auditService;
        this.passwordResets = passwordResets;
    }

    @GetMapping("/csrf")
    public ApiEnvelope<CsrfView> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            throw new IllegalStateException("CSRF token was not initialized");
        }
        return ApiEnvelope.success(new CsrfView(token.getHeaderName(), token.getParameterName(), token.getToken()));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiEnvelope<UserView>> register(@Valid @RequestBody RegisterRequest request) {
        UserView user = identityService.registerStudent(request);
        auditService.record(user.id(), null, "AUTH_REGISTER", "USER", user.id(), AuditService.SUCCEEDED);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(user));
    }

    @PostMapping("/login")
    public ApiEnvelope<AuthSessionView> login(
            @Valid @RequestBody LoginRequest login,
            HttpServletRequest request,
            HttpServletResponse response) {
        String remoteAddress = request.getRemoteAddr();
        String loginKey = login.portal().name() + ":" + login.identifier().trim();
        if (loginAttemptService.isBlocked(loginKey, remoteAddress)) {
            auditService.record(null, null, "AUTH_LOGIN", "USER", null, AuditService.REJECTED);
            throw new AppException(ErrorCode.RATE_LIMITED, "Too many failed login attempts; try again later");
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(loginKey, login.password()));
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            identityService.markLoggedIn(principal.userId());
            loginAttemptService.clear(loginKey, remoteAddress);
            auditService.record(principal.userId(), null, "AUTH_LOGIN", "USER", principal.userId(),
                    AuditService.SUCCEEDED);
            return ApiEnvelope.success(new AuthSessionView(identityService.getUser(principal.userId())));
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            loginAttemptService.recordFailure(loginKey, remoteAddress);
            auditService.record(null, null, "AUTH_LOGIN", "USER", null, AuditService.FAILED);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials or account type");
        }
    }

    @PostMapping("/claim-student-no")
    public ApiEnvelope<UserView> claimStudentNo(@Valid @RequestBody ClaimStudentNoRequest request,
                                                HttpServletRequest servletRequest) {
        String key = "CLAIM:" + request.identifier().trim();
        String remoteAddress = servletRequest.getRemoteAddr();
        if (loginAttemptService.isBlocked(key, remoteAddress)) {
            throw new AppException(ErrorCode.RATE_LIMITED, "Too many failed attempts; try again later");
        }
        try {
            UserView user = identityService.claimStudentNo(request);
            loginAttemptService.clear(key, remoteAddress);
            auditService.record(user.id(), null, "STUDENT_NUMBER_CLAIM", "USER", user.id(), AuditService.SUCCEEDED);
            return ApiEnvelope.success(user);
        } catch (AppException failure) {
            loginAttemptService.recordFailure(key, remoteAddress);
            throw failure;
        }
    }

    @PostMapping("/reset-password")
    public ApiEnvelope<Void> resetPassword(@Valid @RequestBody CompletePasswordResetRequest request) {
        passwordResets.complete(request.token(), request.password());
        return ApiEnvelope.success("Password reset complete", null);
    }

    @PostMapping("/logout")
    public ApiEnvelope<Void> logout(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            auditService.record(principal.userId(), null, "AUTH_LOGOUT", "USER", principal.userId(),
                    AuditService.SUCCEEDED);
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ApiEnvelope.success("Logged out", null);
    }

    @GetMapping("/me")
    public ApiEnvelope<UserView> me(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new AppException(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication required");
        }
        return ApiEnvelope.success(identityService.getUser(userPrincipal.userId()));
    }
}
