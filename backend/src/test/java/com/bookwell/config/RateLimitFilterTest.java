package com.bookwell.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        AppProperties.Security security = new AppProperties.Security(
                "secret-key",
                java.time.Duration.ofMinutes(15),
                java.time.Duration.ofDays(14),
                false,
                "Lax",
                null,
                java.util.List.of(),
                null,
                "bookwell_refresh"
        );
        AppProperties properties = new AppProperties(
                "test",
                security,
                null,
                null,
                null,
                null,
                new AppProperties.RateLimit(true, 3, 5)
        );
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        filter = new RateLimitFilter(properties, mapper);
    }

    @Test
    void encodedPathVariantsShareSameBucketAndAreThrottled() throws ServletException, IOException {
        // Request 1 to plain path
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req1.setServletPath("/api/v1/auth/login");
        req1.setRemoteAddr("192.168.1.10");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, new MockFilterChain());
        assertThat(res1.getStatus()).isEqualTo(200);

        // Request 2 to encoded path (servlet container decodes servletPath)
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/auth/logi%6E");
        req2.setServletPath("/api/v1/auth/login");
        req2.setRemoteAddr("192.168.1.10");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, new MockFilterChain());
        assertThat(res2.getStatus()).isEqualTo(200);

        // Request 3 to path with trailing slash
        MockHttpServletRequest req3 = new MockHttpServletRequest("POST", "/api/v1/auth/login/");
        req3.setServletPath("/api/v1/auth/login/");
        req3.setRemoteAddr("192.168.1.10");
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        filter.doFilter(req3, res3, new MockFilterChain());
        assertThat(res3.getStatus()).isEqualTo(200);

        // Request 4 should be 429 Too Many Requests
        MockHttpServletRequest req4 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req4.setServletPath("/api/v1/auth/login");
        req4.setRemoteAddr("192.168.1.10");
        MockHttpServletResponse res4 = new MockHttpServletResponse();
        filter.doFilter(req4, res4, new MockFilterChain());
        assertThat(res4.getStatus()).isEqualTo(429);
        assertThat(res4.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void refreshEndpointUsesSeparateRefreshLimit() throws ServletException, IOException {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
            req.setServletPath("/api/v1/auth/refresh");
            req.setRemoteAddr("192.168.1.20");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest req6 = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        req6.setServletPath("/api/v1/auth/refresh");
        req6.setRemoteAddr("192.168.1.20");
        MockHttpServletResponse res6 = new MockHttpServletResponse();
        filter.doFilter(req6, res6, new MockFilterChain());
        assertThat(res6.getStatus()).isEqualTo(429);
    }
}
