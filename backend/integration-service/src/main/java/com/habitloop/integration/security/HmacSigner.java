package com.habitloop.integration.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** HMAC-SHA256 signing and constant-time verification of webhook bodies (docs/08). */
public final class HmacSigner {

    private static final String ALGO = "HmacSHA256";

    private HmacSigner() {
    }

    public static String hex(String secret, String body) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
            byte[] raw = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    /** Constant-time comparison to avoid timing attacks. */
    public static boolean verify(String secret, String body, String providedHex) {
        if (providedHex == null) {
            return false;
        }
        String expected = hex(secret, body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
