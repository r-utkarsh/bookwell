package com.bookwell.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestMetadataTest {

    @Test
    void clientIpUsesRightmostHopFromXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.195");

        // When trusted, it extracts the rightmost (last trusted proxy hop) rather than the client-injected leftmost
        String ip = RequestMetadata.clientIp(request, true);
        assertEquals("203.0.113.195", ip);
    }

    @Test
    void clientIpPrefersCloudflareConnectingIpWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("CF-Connecting-IP", "203.0.113.50");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.2");

        String ip = RequestMetadata.clientIp(request, true);
        assertEquals("203.0.113.50", ip);
    }

    @Test
    void clientIpPrefersXRealIpOverXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.77");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.2");

        String ip = RequestMetadata.clientIp(request, true);
        assertEquals("203.0.113.77", ip);
    }

    @Test
    void clientIpFallsBackToRemoteAddrWhenUntrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-Forwarded-For", "198.51.100.1");

        String ip = RequestMetadata.clientIp(request, false);
        assertEquals("192.168.1.100", ip);
    }
}
