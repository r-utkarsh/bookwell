package com.bookwell.common.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthUser currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            return null;
        }
        return user;
    }

    public static UUID currentUserId() {
        AuthUser user = currentUser();
        return user == null ? null : user.id();
    }

    public static boolean hasRole(com.bookwell.user.Role role) {
        AuthUser user = currentUser();
        return user != null && user.role() == role;
    }
}
