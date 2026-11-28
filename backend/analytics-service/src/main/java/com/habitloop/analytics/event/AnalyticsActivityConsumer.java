package com.habitloop.analytics.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.habitloop.analytics.service.AnalyticsIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Consumes {@code activity.logged} and updates aggregated analytics. Active only when
 * {@code habitloop.kafka.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "kafka")
public class AnalyticsActivityConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsActivityConsumer.class);
    private final AnalyticsIngestService ingestService;
    private final ObjectMapper objectMapper;

    public AnalyticsActivityConsumer(AnalyticsIngestService ingestService, ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "activity.logged", groupId = "analytics-service")
    public void onMessage(String message) {
        JsonNode payload;
        OffsetDateTime occurredAt;
        try {
            JsonNode envelope = objectMapper.readTree(message);
            if (envelope == null || !envelope.path("payload").isObject()) {
                throw new InvalidActivityEventException("Missing activity payload");
            }
            payload = envelope.path("payload");
            occurredAt = OffsetDateTime.parse(requiredText(payload, "occurredAt"));
        } catch (JsonProcessingException | DateTimeParseException e) {
            throw new InvalidActivityEventException("Invalid activity.logged message", e);
        }
        String tenantId = requiredText(payload, "tenantId");
        String userId = requiredText(payload, "userId");
        String activityLogId = requiredText(payload, "activityLogId");
        try {
            ingestService.apply(tenantId, userId, activityLogId, occurredAt);
        } catch (RuntimeException e) {
            log.error("Failed to aggregate activity {}; the message will be retried", activityLogId, e);
            throw e;
        }
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new InvalidActivityEventException("Missing or invalid " + field);
        }
        return value.asText();
    }

    public static class InvalidActivityEventException extends RuntimeException {
        public InvalidActivityEventException(String message) {
            super(message);
        }

        public InvalidActivityEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
