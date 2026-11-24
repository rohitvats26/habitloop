package com.habitloop.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration, bound from {@code habitloop.jwt.*}. The secret comes from the
 * environment / Secrets Manager — never hardcoded (see docs/08).
 */
@ConfigurationProperties(prefix = "habitloop.jwt")
public class JwtProperties {

    /** HMAC signing secret (>= 32 bytes for HS256). Injected from env/Secrets Manager. */
    private String secret = "change-me-in-env-this-is-only-a-dev-placeholder-secret-32b";

    /** Token issuer claim. */
    private String issuer = "habitloop";

    /** Access-token lifetime in seconds (default 1h). */
    private long accessTokenSeconds = 3600;

    /** Refresh-token lifetime in seconds (default 14d). */
    private long refreshTokenSeconds = 1_209_600;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTokenSeconds() {
        return accessTokenSeconds;
    }

    public void setAccessTokenSeconds(long accessTokenSeconds) {
        this.accessTokenSeconds = accessTokenSeconds;
    }

    public long getRefreshTokenSeconds() {
        return refreshTokenSeconds;
    }

    public void setRefreshTokenSeconds(long refreshTokenSeconds) {
        this.refreshTokenSeconds = refreshTokenSeconds;
    }
}
