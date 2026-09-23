package com.ustb.seforge.identity.api;

import com.ustb.seforge.common.api.ApiEnvelope;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.identity.security.UserPrincipal;
import com.ustb.seforge.identity.service.IdentityService;
import com.ustb.seforge.identity.service.LoginAttemptService;
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

    public AuthController(
            IdentityService identityService,
            LoginAttemptService loginAttemptService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            AuditService auditService) {
        this.identityService = identityService;
        this.loginAttemptService = loginAttemptService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.auditService = auditService;
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
        if (loginAttemptService.isBlocked(login.identifier(), remoteAddress)) {
            auditService.record(null, null, "AUTH_LOGIN", "USER", null, AuditService.REJECTED);
            throw new AppException(ErrorCode.RATE_LIMITED, "Too many failed login attempts; try again later");
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.identifier(), login.password()));
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            identityService.markLoggedIn(principal.userId());
            loginAttemptService.clear(login.identifier(), remoteAddress);
            auditService.record(principal.userId(), null, "AUTH_LOGIN", "USER", principal.userId(),
                    AuditService.SUCCEEDED);
            return ApiEnvelope.success(new AuthSessionView(identityService.getUser(principal.userId())));
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            loginAttemptService.recordFailure(login.identifier(), remoteAddress);
            auditService.record(null, null, "AUTH_LOGIN", "USER", null, AuditService.FAILED);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Invalid username/email or password");
        }
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
