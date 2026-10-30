package com.bookwell.config;

import com.bookwell.common.api.ApiError;
import com.bookwell.common.security.RequestMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small fixed-window per-IP rate limiter for authentication endpoints.
 * It is intentionally in-memory; see README's known limitations for multi-instance deployments.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/register/provider"
    );
    private static final long WINDOW_MILLIS = 60_000L;

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.rateLimit().enabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = RequestMetadata.clientIp(request, properties.security().trustForwardedHeaders());
        String key = ip + ":" + request.getRequestURI();
        long now = System.currentTimeMillis();
        Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(now));
        boolean allowed;
        synchronized (counter) {
            if (now - counter.windowStart >= WINDOW_MILLIS) {
                counter.windowStart = now;
                counter.count = 0;
            }
            counter.count++;
            allowed = counter.count <= properties.rateLimit().authRequestsPerMinute();
        }
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(entry -> now - entry.getValue().windowStart > WINDOW_MILLIS * 5);
        }
        if (!allowed) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ApiError(
                    Instant.now(), 429, "RATE_LIMITED",
                    "Too many attempts. Please wait a minute and try again.", request.getRequestURI(), null));
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Counter {
        private long windowStart;
        private int count;

        private Counter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
