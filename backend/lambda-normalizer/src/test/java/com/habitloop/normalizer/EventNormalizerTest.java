package com.habitloop.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final EventNormalizer normalizer = new EventNormalizer(mapper);

    @Test
    void normalizesValidEvent_toCanonicalActivity() throws Exception {
        String raw = "{\"externalEventId\":\"evt_1\",\"type\":\"STEPS\",\"value\":8450,"
                + "\"unit\":\"steps\",\"occurredAt\":\"2026-11-23T06:40:00Z\"}";
        String received = envelope("t1", "u9", "evt_1", raw);

        String out = normalizer.normalize(received);

        assertThat(out).isNotNull();
        JsonNode payload = mapper.readTree(out).path("payload");
        assertThat(payload.path("tenantId").asText()).isEqualTo("t1");
        assertThat(payload.path("userId").asText()).isEqualTo("u9");
        assertThat(payload.path("activityType").asText()).isEqualTo("STEPS");
        assertThat(payload.path("value").asInt()).isEqualTo(8450);
        assertThat(payload.path("source").asText()).isEqualTo("WEARABLE");
        assertThat(payload.path("externalEventId").asText()).isEqualTo("evt_1");
    }

    @Test
    void returnsNull_whenRequiredFieldMissing() throws Exception {
        String raw = "{\"externalEventId\":\"evt_1\",\"type\":\"STEPS\"}";
        String received = envelope("t1", "", "evt_1", raw); // blank userId
        assertThat(normalizer.normalize(received)).isNull();
    }

    private String envelope(String tenantId, String userId, String externalEventId, String rawJson)
            throws Exception {
        var env = mapper.createObjectNode();
        env.put("eventType", "partner.event.received");
        env.put("tenantId", tenantId);
        var payload = mapper.createObjectNode();
        payload.put("tenantId", tenantId);
        payload.put("userId", userId);
        payload.put("externalEventId", externalEventId);
        payload.put("rawPayload", rawJson);
        env.set("payload", payload);
        return mapper.writeValueAsString(env);
    }
}
