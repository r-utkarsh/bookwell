package com.bookwell.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginAttemptLimiter();
    }

    @Test
    void blocksAfterFiveFailuresFromSameIp() {
        String email = "victim@example.com";
        String ip = "1.2.3.4";

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(email, ip);
            assertThat(limiter.isBlocked(email, ip)).isFalse();
        }

        limiter.recordFailure(email, ip); // 5th failure
        assertThat(limiter.isBlocked(email, ip)).isTrue();

        // Successful login clears the per-IP lock
        limiter.recordSuccess(email, ip);
        assertThat(limiter.isBlocked(email, ip)).isFalse();
    }

    @Test
    void blocksAfterTwentyFiveFailuresAcrossDifferentIps() {
        String email = "victim2@example.com";

        for (int i = 0; i < 24; i++) {
            limiter.recordFailure(email, "10.0.0." + i);
            assertThat(limiter.isBlocked(email, "10.0.0.999")).isFalse();
        }

        limiter.recordFailure(email, "10.0.0.24"); // 25th failure globally
        assertThat(limiter.isBlocked(email, "10.0.0.999")).isTrue();
    }
}
