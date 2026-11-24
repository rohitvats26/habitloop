package com.habitloop.common.security;

import java.util.List;

/**
 * Authenticated principal derived from the JWT. Stored as the Spring Security
 * Authentication principal so controllers/services can read the current user and tenant.
 */
public record AuthUser(String userId, String tenantId, List<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
