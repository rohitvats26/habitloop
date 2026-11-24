package com.habitloop.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login request. {@code tenantCode} is optional and only needed to disambiguate when the same email
 * exists in more than one tenant.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        String tenantCode
) {
}
