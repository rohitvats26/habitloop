package com.habitloop.integration.security;

/**
 * Stores and resolves per-connection HMAC secrets by reference. RDS keeps only the reference; the secret
 * lives here. The dev implementation is in-memory; in AWS this is backed by Secrets Manager (docs/06/08).
 */
public interface SecretStore {

    /** Persist {@code secret} under {@code ref} (ref is what gets stored in RDS). */
    void store(String ref, String secret);

    /** Resolve the secret for {@code ref}, or null if unknown. */
    String resolve(String ref);
}
