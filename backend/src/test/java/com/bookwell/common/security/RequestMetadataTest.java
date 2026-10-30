package com.bookwell.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestMetadataTest {

    @Test
    void clientIpUsesRightmostHopFromXForwardedForWhenConfigured() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.195");

        String ip = RequestMetadata.clientIp(request, "X-Forwarded-For");
        assertEquals("203.0.113.195", ip);
    }

    @Test
    void clientIpUsesConfiguredDirectHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("CF-Connecting-IP", "203.0.113.50");

        String ip = RequestMetadata.clientIp(request, "CF-Connecting-IP");
        assertEquals("203.0.113.50", ip);
    }

    @Test
    void clientIpFallsBackToRemoteAddrWhenHeaderNullOrEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-Forwarded-For", "198.51.100.1");

        String ip = RequestMetadata.clientIp(request, null);
        assertEquals("192.168.1.100", ip);

        String ipBlank = RequestMetadata.clientIp(request, "   ");
        assertEquals("192.168.1.100", ipBlank);
    }

    @Test
    void clientIpRejectsMalformedHeaderStrings() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-Real-IP", "malformed<script>bad</script>");

        String ip = RequestMetadata.clientIp(request, "X-Real-IP");
        assertEquals("192.168.1.100", ip);
    }
}
