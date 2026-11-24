package com.habitloop.integration.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignerTest {

    private static final String SECRET = "super-secret-key";
    private static final String BODY = "{\"externalEventId\":\"evt_1\",\"type\":\"STEPS\",\"value\":8450}";

    @Test
    void hex_isDeterministic() {
        assertThat(HmacSigner.hex(SECRET, BODY)).isEqualTo(HmacSigner.hex(SECRET, BODY));
    }

    @Test
    void verify_acceptsCorrectSignature() {
        String sig = HmacSigner.hex(SECRET, BODY);
        assertThat(HmacSigner.verify(SECRET, BODY, sig)).isTrue();
    }

    @Test
    void verify_rejectsWrongSignatureAndNull() {
        assertThat(HmacSigner.verify(SECRET, BODY, "deadbeef")).isFalse();
        assertThat(HmacSigner.verify(SECRET, BODY, null)).isFalse();
        assertThat(HmacSigner.verify("other-secret", BODY, HmacSigner.hex(SECRET, BODY))).isFalse();
    }
}
