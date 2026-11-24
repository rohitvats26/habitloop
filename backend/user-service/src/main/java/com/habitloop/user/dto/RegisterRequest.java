package com.habitloop.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration request. Provide either {@code tenantName} (creates a new tenant; the user becomes
 * TENANT_ADMIN) or {@code joinCode} (joins an existing tenant as USER).
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 80) String displayName,
        @Size(max = 64) String timezone,
        @Size(max = 120) String tenantName,
        @Size(max = 16) String joinCode
) {
}
