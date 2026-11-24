package com.habitloop.user.dto;

import java.util.List;

public record UserResponse(
        String id,
        String tenantId,
        String email,
        String displayName,
        String timezone,
        String avatarUrl,
        List<String> roles
) {
}
