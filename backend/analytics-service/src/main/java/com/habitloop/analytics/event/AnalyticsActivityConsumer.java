package com.habitloop.analytics.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.analytics.service.AnalyticsIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

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
        try {
            JsonNode payload = objectMapper.readTree(message).path("payload");
            String tenantId = payload.path("tenantId").asText();
            String userId = payload.path("userId").asText();
            String activityLogId = payload.path("activityLogId").asText();
            OffsetDateTime occurredAt = OffsetDateTime.parse(payload.path("occurredAt").asText());
            ingestService.apply(tenantId, userId, activityLogId, occurredAt);
        } catch (Exception e) {
            log.error("Failed to process activity.logged message", e);
        }
    }
}
