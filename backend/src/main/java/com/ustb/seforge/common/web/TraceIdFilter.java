package com.ustb.seforge.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requested = request.getHeader(HEADER);
        String traceId = isValid(requested) ? requested : UUID.randomUUID().toString();
        MDC.put(TraceContext.TRACE_ID, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.TRACE_ID);
        }
    }

    private boolean isValid(String value) {
        return value != null && value.length() >= 8 && value.length() <= 64
                && value.chars().allMatch(character -> Character.isLetterOrDigit(character) || "-_".indexOf(character) >= 0);
    }
}
