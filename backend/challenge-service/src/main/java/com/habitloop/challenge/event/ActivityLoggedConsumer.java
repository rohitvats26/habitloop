package com.habitloop.challenge.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.challenge.service.ProgressService;
import com.habitloop.challenge.service.AchievementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.time.OffsetDateTime;

/**
 * Consumes {@code activity.logged} and updates challenge progress. Challenge progress is driven entirely
 * by events — no synchronous call to activity-service (NFR-9). Active only when
 * {@code habitloop.kafka.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "kafka")
public class ActivityLoggedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ActivityLoggedConsumer.class);

    private final ProgressService progressService;
    private final AchievementService achievementService;
    private final ObjectMapper objectMapper;

    public ActivityLoggedConsumer(ProgressService progressService, AchievementService achievementService,
                                  ObjectMapper objectMapper) {
        this.progressService = progressService;
        this.achievementService = achievementService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "activity.logged", groupId = "challenge-service")
    public void onMessage(String message) {
        try {
            JsonNode payload = objectMapper.readTree(message).path("payload");
            UUID tenantId = UUID.fromString(payload.path("tenantId").asText());
            UUID userId = UUID.fromString(payload.path("userId").asText());
            UUID activityLogId = UUID.fromString(payload.path("activityLogId").asText());
            OffsetDateTime occurredAt = OffsetDateTime.parse(payload.path("occurredAt").asText());
            progressService.applyActivity(tenantId, userId, activityLogId, occurredAt);
            achievementService.recordActivity(tenantId, userId, activityLogId,
                    payload.path("activityType").asText(), occurredAt);
        } catch (Exception e) {
            log.error("Failed to process activity.logged message", e);
            throw new IllegalStateException("Failed to process activity.logged message", e);
        }
    }
}
