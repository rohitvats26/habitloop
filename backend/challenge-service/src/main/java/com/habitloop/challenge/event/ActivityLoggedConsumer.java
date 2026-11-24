package com.habitloop.challenge.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.challenge.service.ProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@code activity.logged} and updates challenge progress. Challenge progress is driven entirely
 * by events — no synchronous call to activity-service (NFR-9). Active only when
 * {@code habitloop.kafka.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.kafka.enabled", havingValue = "true")
public class ActivityLoggedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ActivityLoggedConsumer.class);

    private final ProgressService progressService;
    private final ObjectMapper objectMapper;

    public ActivityLoggedConsumer(ProgressService progressService, ObjectMapper objectMapper) {
        this.progressService = progressService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "activity.logged", groupId = "challenge-service")
    public void onMessage(String message) {
        try {
            JsonNode payload = objectMapper.readTree(message).path("payload");
            UUID tenantId = UUID.fromString(payload.path("tenantId").asText());
            UUID userId = UUID.fromString(payload.path("userId").asText());
            UUID activityLogId = UUID.fromString(payload.path("activityLogId").asText());
            progressService.applyActivity(tenantId, userId, activityLogId);
        } catch (Exception e) {
            log.error("Failed to process activity.logged message", e);
        }
    }
}
