package com.ustb.seforge.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.common.api.ApiError;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.identity.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final ApiRateLimitService limits;
    private final ObjectMapper objectMapper;
    private final int generalLimit;
    private final int expensiveLimit;
    private final int uploadLimit;
    private final Duration window;

    public ApiRateLimitFilter(ApiRateLimitService limits, ObjectMapper objectMapper,
                              int generalLimit, int expensiveLimit, int uploadLimit,
                              Duration window) {
        this.limits = limits;
        this.objectMapper = objectMapper;
        this.generalLimit = generalLimit;
        this.expensiveLimit = expensiveLimit;
        this.uploadLimit = uploadLimit;
        this.window = window;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equals(request.getMethod())
                || path.startsWith("/actuator/health")
                || path.equals("/api/v1/auth/csrf");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Limit limit = classify(request);
        if (!limits.allow(subject(request), limit.bucket(), limit.maximum(), window)) {
            response.setStatus(ErrorCode.RATE_LIMITED.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", Long.toString(Math.max(1, window.toSeconds())));
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                    ErrorCode.RATE_LIMITED.name(), "Too many requests; retry after the current window"));
            return;
        }
        chain.doFilter(request, response);
    }

    private Limit classify(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (request.getContentType() != null
                && request.getContentType().toLowerCase().startsWith("multipart/")) {
            return new Limit("upload", uploadLimit);
        }
        if ("POST".equals(method) && (path.endsWith("/messages") || path.endsWith("/tutor")
                || path.contains("/reviews") || path.contains("/analytics/snapshots"))) {
            return new Limit("expensive", expensiveLimit);
        }
        return new Limit("general", generalLimit);
    }

    private String subject(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return "user:" + principal.userId();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private record Limit(String bucket, int maximum) {
    }
}
