package com.habitloop.challenge.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.challenge.service.AchievementService;
import com.habitloop.challenge.service.ProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Component
public class ActivityEventHandler {
    private static final Logger log = LoggerFactory.getLogger(ActivityEventHandler.class);
    private final ObjectMapper mapper;
    private final ProgressService progress;
    private final AchievementService achievements;

    public ActivityEventHandler(ObjectMapper mapper, ProgressService progress, AchievementService achievements) {
        this.mapper = mapper;
        this.progress = progress;
        this.achievements = achievements;
    }

    public void handle(String message) {
        Activity event = parse(message);
        try {
            progress.applyActivity(event.tenantId(), event.userId(), event.activityId(), event.occurredAt());
            achievements.recordActivity(event.tenantId(), event.userId(), event.activityId(), event.habitId(),
                    event.type(), event.occurredAt());
        } catch (RuntimeException e) {
            log.error("Failed to process activity {}; the message will be retried", event.activityId(), e);
            throw e;
        }
    }

    private Activity parse(String message) {
        try {
            JsonNode root = mapper.readTree(message);
            if (root == null || !root.path("payload").isObject()) {
                throw new IllegalArgumentException("Missing activity payload");
            }
            JsonNode payload = root.path("payload");
            JsonNode habit = payload.get("habitId");
            UUID habitId = habit == null || habit.isNull() ? null : UUID.fromString(requiredText(payload, "habitId"));
            return new Activity(UUID.fromString(requiredText(payload, "tenantId")),
                    UUID.fromString(requiredText(payload, "userId")),
                    UUID.fromString(requiredText(payload, "activityLogId")), habitId,
                    requiredText(payload, "activityType"), OffsetDateTime.parse(requiredText(payload, "occurredAt")));
        } catch (JsonProcessingException | IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidActivityEventException("Invalid activity.logged message", e);
        }
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing or invalid " + field);
        }
        return value.asText();
    }

    public static class InvalidActivityEventException extends RuntimeException {
        public InvalidActivityEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record Activity(UUID tenantId, UUID userId, UUID activityId, UUID habitId,
                            String type, OffsetDateTime occurredAt) {
    }
}
