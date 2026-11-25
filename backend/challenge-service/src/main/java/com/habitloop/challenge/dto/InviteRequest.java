package com.habitloop.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record InviteRequest(
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "must be a valid user ID (UUID); email addresses are not accepted")
        String userId
) {
    public UUID userUuid() {
        return UUID.fromString(userId);
    }
}
