package com.habitloop.activity.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.activity.service.ActivityIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code partner.activity.normalized} (the Lambda's output) and turns each event into an
 * idempotent activity log. At-least-once delivery + the activity_log unique key = effectively-once,
 * so replays never create duplicate logs (NFR-2). Active only when {@code habitloop.kafka.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.kafka.enabled", havingValue = "true")
public class PartnerActivityConsumer {

    private static final Logger log = LoggerFactory.getLogger(PartnerActivityConsumer.class);

    private final ActivityIngestService ingestService;
    private final ObjectMapper objectMapper;

    public PartnerActivityConsumer(ActivityIngestService ingestService, ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "partner.activity.normalized", groupId = "activity-service")
    public void onMessage(String message) {
        try {
            NormalizedPartnerActivity activity = objectMapper.readValue(message, NormalizedPartnerActivity.class);
            ingestService.recordExternalActivity(activity);
        } catch (Exception e) {
            // Malformed payloads are logged and dropped here; the pipeline's DLQ handles upstream failures.
            log.error("Failed to process partner.activity.normalized message", e);
        }
    }
}
