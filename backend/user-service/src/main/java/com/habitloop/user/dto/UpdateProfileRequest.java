package com.habitloop.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 80) String displayName,
        @Size(max = 64) String timezone,
        @Size(max = 512) String avatarUrl
) {
}
