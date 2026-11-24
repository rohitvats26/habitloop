package com.habitloop.user.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        String userId,
        String tenantId
) {
    public static AuthResponse bearer(String access, String refresh, long expiresIn,
                                       String userId, String tenantId) {
        return new AuthResponse(access, refresh, expiresIn, "Bearer", userId, tenantId);
    }
}
