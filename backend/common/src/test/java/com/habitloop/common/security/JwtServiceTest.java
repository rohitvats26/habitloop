package com.habitloop.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = newService();

    private static JwtService newService() {
        JwtProperties props = new JwtProperties();
        props.setSecret("unit-test-secret-key-that-is-at-least-32-bytes-long!!");
        props.setIssuer("habitloop");
        props.setAccessTokenSeconds(3600);
        props.setRefreshTokenSeconds(7200);
        return new JwtService(props);
    }

    @Test
    void accessToken_roundTrips_withClaims() {
        String token = jwtService.generateAccessToken("user-1", "tenant-1", List.of("USER", "TENANT_ADMIN"));

        Claims claims = jwtService.parse(token);
        AuthUser user = jwtService.toAuthUser(claims);

        assertThat(user.userId()).isEqualTo("user-1");
        assertThat(user.tenantId()).isEqualTo("tenant-1");
        assertThat(user.roles()).containsExactlyInAnyOrder("USER", "TENANT_ADMIN");
        assertThat(claims.get(JwtService.CLAIM_TYPE, String.class)).isEqualTo(JwtService.TYPE_ACCESS);
    }

    @Test
    void refreshToken_hasRefreshType() {
        String token = jwtService.generateRefreshToken("user-9", "tenant-9", List.of("USER"));
        Claims claims = jwtService.parse(token);
        assertThat(claims.get(JwtService.CLAIM_TYPE, String.class)).isEqualTo(JwtService.TYPE_REFRESH);
        assertThat(claims.getSubject()).isEqualTo("user-9");
    }

    @Test
    void tamperedToken_failsVerification() {
        String token = jwtService.generateAccessToken("u", "t", List.of("USER"));
        String tampered = token.substring(0, token.length() - 2) + "xx";
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> jwtService.parse(tampered));
    }
}
