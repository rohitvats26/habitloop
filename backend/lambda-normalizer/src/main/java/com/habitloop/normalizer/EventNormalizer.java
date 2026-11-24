package com.habitloop.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pure validation + enrichment + normalization logic, shared between the local Kafka consumer and the AWS
 * Lambda handler. Input: a {@code partner.event.received} envelope. Output: a
 * {@code partner.activity.normalized} envelope, or null if the event is invalid (dropped / DLQ upstream).
 */
public class EventNormalizer {

    private final ObjectMapper mapper;

    public EventNormalizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String normalize(String receivedEnvelopeJson) throws Exception {
        JsonNode root = mapper.readTree(receivedEnvelopeJson);
        JsonNode payload = root.path("payload");

        String tenantId = payload.path("tenantId").asText(null);
        String userId = payload.path("userId").asText(null);
        String externalEventId = payload.path("externalEventId").asText(null);
        JsonNode raw = parseRaw(payload.path("rawPayload"));

        // Validation: required fields.
        if (isBlank(tenantId) || isBlank(userId) || isBlank(externalEventId) || raw == null) {
            return null;
        }

        // Normalization: map partner fields to HabitLoop's canonical activity shape.
        String type = raw.path("type").asText("ACTIVITY");
        OffsetDateTime occurredAt = raw.hasNonNull("occurredAt")
                ? OffsetDateTime.parse(raw.path("occurredAt").asText())
                : OffsetDateTime.now();

        ObjectNode activity = mapper.createObjectNode();
        activity.put("tenantId", tenantId);
        activity.put("userId", userId);
        if (raw.hasNonNull("habitId")) {
            activity.put("habitId", raw.path("habitId").asText());
        } else {
            activity.putNull("habitId");
        }
        activity.put("activityType", type);
        if (raw.hasNonNull("value")) {
            activity.put("value", raw.path("value").decimalValue());
        } else {
            activity.putNull("value");
        }
        activity.put("unit", raw.path("unit").asText(null));
        activity.put("occurredAt", occurredAt.toString());
        activity.put("externalEventId", externalEventId);
        activity.put("source", "WEARABLE");

        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "partner.activity.normalized");
        envelope.put("tenantId", tenantId);
        envelope.put("occurredAt", OffsetDateTime.now().toString());
        envelope.put("idempotencyKey", tenantId + ":" + userId + ":WEARABLE:" + externalEventId);
        envelope.set("payload", activity);
        return mapper.writeValueAsString(envelope);
    }

    private JsonNode parseRaw(JsonNode rawPayloadNode) {
        try {
            if (rawPayloadNode.isTextual()) {
                return mapper.readTree(rawPayloadNode.asText());
            }
            return rawPayloadNode.isMissingNode() || rawPayloadNode.isNull() ? null : rawPayloadNode;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
