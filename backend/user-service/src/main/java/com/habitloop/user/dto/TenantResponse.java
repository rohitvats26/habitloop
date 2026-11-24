package com.habitloop.user.dto;

public record TenantResponse(
        String id,
        String name,
        String joinCode,
        String status
) {
}
