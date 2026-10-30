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
import java.util.concurrent.atomic.AtomicLong;

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
    private static final int MAX_ENTRIES = 10_000;

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastCleanup = new AtomicLong(0);
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
        long now = System.currentTimeMillis();

        // Throttled cleanup: runs at most once per WINDOW_MILLIS across threads
        long last = lastCleanup.get();
        if (now - last > WINDOW_MILLIS && lastCleanup.compareAndSet(last, now)) {
            counters.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= WINDOW_MILLIS);
        }

        // Hard capacity safeguard against rapid memory exhaustion attacks
        if (counters.size() >= MAX_ENTRIES) {
            counters.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= WINDOW_MILLIS);
            if (counters.size() >= MAX_ENTRIES) {
                counters.clear();
            }
        }

        String ip = RequestMetadata.clientIp(request, properties.security().trustForwardedHeaders());
        String key = ip + ":" + request.getRequestURI();
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
