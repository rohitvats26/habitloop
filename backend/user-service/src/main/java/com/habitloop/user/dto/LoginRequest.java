package com.habitloop.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request. {@code tenantCode} is optional and only needed to disambiguate when the same email
 * exists in more than one tenant.
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password,
        @Size(max = 16)
        String tenantCode
) {
}
