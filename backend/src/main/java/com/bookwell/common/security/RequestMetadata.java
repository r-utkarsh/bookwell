package com.bookwell.common.security;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestMetadata {

    private RequestMetadata() {
    }

    public static String clientIp(HttpServletRequest request, boolean trustForwardedHeaders) {
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        return value == null ? null : value.substring(0, Math.min(value.length(), 255));
    }
}
