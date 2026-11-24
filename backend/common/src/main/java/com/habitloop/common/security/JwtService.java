package com.habitloop.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Issues and verifies JWTs. HS256 for dev; the secret is supplied via {@link JwtProperties}
 * (env / Secrets Manager). Tokens carry subject=userId plus tenantId and roles claims.
 */
@Component
public class JwtService {

    public static final String CLAIM_TENANT = "tenantId";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String userId, String tenantId, List<String> roles) {
        return build(userId, tenantId, roles, TYPE_ACCESS, props.getAccessTokenSeconds());
    }

    public String generateRefreshToken(String userId, String tenantId, List<String> roles) {
        return build(userId, tenantId, roles, TYPE_REFRESH, props.getRefreshTokenSeconds());
    }

    private String build(String userId, String tenantId, List<String> roles, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .issuer(props.getIssuer())
                .claim(CLAIM_TENANT, tenantId)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /** Parses and verifies a token, returning its claims. Throws JwtException if invalid/expired. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    public AuthUser toAuthUser(Claims claims) {
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        return new AuthUser(claims.getSubject(), claims.get(CLAIM_TENANT, String.class),
                roles == null ? List.of() : roles);
    }

    public long getAccessTokenSeconds() {
        return props.getAccessTokenSeconds();
    }
}
