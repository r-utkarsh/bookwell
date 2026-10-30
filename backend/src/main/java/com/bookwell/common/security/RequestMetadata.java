package com.bookwell.common.security;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestMetadata {

    private RequestMetadata() {
    }

    public static String clientIp(HttpServletRequest request, String trustedHeader) {
        if (trustedHeader != null && !trustedHeader.isBlank()) {
            String value = request.getHeader(trustedHeader);
            if (value != null && !value.isBlank()) {
                String candidate;
                if ("x-forwarded-for".equalsIgnoreCase(trustedHeader.trim())) {
                    String[] parts = value.split(",");
                    candidate = parts[parts.length - 1].trim();
                } else {
                    candidate = value.split(",")[0].trim();
                }
                if (candidate.matches("^[0-9a-fA-F:.]{3,45}$")) {
                    return candidate;
                }
            }
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        return value == null ? null : value.substring(0, Math.min(value.length(), 255));
    }
}
