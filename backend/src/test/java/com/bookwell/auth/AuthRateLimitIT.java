package com.bookwell.auth;

import com.bookwell.config.RateLimitFilter;
import com.bookwell.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.auth-requests-per-minute=3"
})
class AuthRateLimitIT extends IntegrationTestBase {

    private static final String REGISTER_URL = "/api/v1/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @org.junit.jupiter.api.BeforeEach
    void resetLimits() {
        rateLimitFilter.clear();
    }

    @Test
    void fourthLoginAttemptFromOneIpIsRateLimited() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@test.local\",\"password\":\"WrongPass!2026\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.local\",\"password\":\"WrongPass!2026\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void registrationUsesItsOwnRateLimitBucket() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"bad-email\",\"password\":\"short\",\"fullName\":\"X\"}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad-email\",\"password\":\"short\",\"fullName\":\"X\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void encodedPathVariantsShareTheSameBucket() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(java.net.URI.create("/api/v1/auth/logi%6E"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@test.local\",\"password\":\"WrongPass!2026\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.local\",\"password\":\"WrongPass!2026\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
