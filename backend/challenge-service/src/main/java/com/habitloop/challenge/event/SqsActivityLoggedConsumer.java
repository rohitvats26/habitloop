package com.habitloop.challenge.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.challenge.service.ProgressService;
import com.habitloop.challenge.service.AchievementService;
import com.habitloop.common.event.eventbridge.SqsEventPoller;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.UUID;
import java.time.OffsetDateTime;

/**
 * EventBridge counterpart of {@link ActivityLoggedConsumer}: polls this service's SQS queue (fed by the
 * {@code activity.logged} EventBridge rule) and updates challenge progress. Active only when
 * {@code habitloop.events.transport=eventbridge}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "eventbridge")
public class SqsActivityLoggedConsumer {

    private final ProgressService progressService;
    private final AchievementService achievementService;
    private final ObjectMapper objectMapper;
    private final SqsEventPoller poller;

    public SqsActivityLoggedConsumer(SqsClient sqs, ObjectMapper objectMapper,
                                     ProgressService progressService,
                                     AchievementService achievementService,
                                     @Value("${habitloop.events.queue-url:}") String queueUrl) {
        this.progressService = progressService;
        this.achievementService = achievementService;
        this.objectMapper = objectMapper;
        this.poller = new SqsEventPoller(sqs, objectMapper, queueUrl, "challenge", this::handle);
    }

    private void handle(String envelope) {
        try {
            JsonNode payload = objectMapper.readTree(envelope).path("payload");
            UUID tenantId = UUID.fromString(payload.path("tenantId").asText());
            UUID userId = UUID.fromString(payload.path("userId").asText());
            UUID activityLogId = UUID.fromString(payload.path("activityLogId").asText());
            OffsetDateTime occurredAt = OffsetDateTime.parse(payload.path("occurredAt").asText());
            progressService.applyActivity(tenantId, userId, activityLogId, occurredAt);
            achievementService.recordActivity(tenantId, userId, activityLogId,
                    payload.path("activityType").asText(), occurredAt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process activity.logged", e);
        }
    }

    @PostConstruct
    void start() {
        poller.start();
    }

    @PreDestroy
    void stop() {
        poller.stop();
    }
}
