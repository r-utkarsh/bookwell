package com.bookwell.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Every integration test runs against one deterministic instant so that
 * lead-time, cancellation-cutoff, and calendar assertions never depend on the
 * wall clock. The instant is a Monday morning in UTC (05:30 in Asia/Kolkata).
 */
@TestConfiguration
public class TestClockConfig {

    public static final Instant FIXED_INSTANT = Instant.parse("2027-03-01T00:00:00Z");

    @Bean
    @Primary
    public Clock fixedTestClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }
}
