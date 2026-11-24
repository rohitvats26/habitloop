package com.habitloop.common.security;

import com.habitloop.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience accessors for the authenticated {@link AuthUser}. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new UnauthorizedException("No authenticated user in context");
        }
        return user;
    }

    public static String currentUserId() {
        return currentUser().userId();
    }

    public static String currentTenantId() {
        return currentUser().tenantId();
    }
}
