package com.habitloop.analytics.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.analytics.service.AnalyticsIngestService;
import com.habitloop.common.event.EventEnvelope;
import com.habitloop.common.event.eventbridge.EventBridgePublisher;
import com.habitloop.common.event.eventbridge.SqsEventPoller;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * EventBridge counterpart of {@link AnalyticsActivityConsumer}: polls this service's SQS queue (fed by the
 * {@code activity.logged} rule), updates aggregated analytics, then publishes {@code analytics.updated} back
 * to the bus so recommendation-service can refresh. Active only when
 * {@code habitloop.events.transport=eventbridge}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "eventbridge")
public class SqsAnalyticsConsumer {

    private final AnalyticsIngestService ingestService;
    private final EventBridgePublisher publisher;
    private final ObjectMapper objectMapper;
    private final SqsEventPoller poller;

    public SqsAnalyticsConsumer(SqsClient sqs, ObjectMapper objectMapper,
                                AnalyticsIngestService ingestService, EventBridgePublisher publisher,
                                @Value("${habitloop.events.queue-url:}") String queueUrl) {
        this.ingestService = ingestService;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.poller = new SqsEventPoller(sqs, objectMapper, queueUrl, "analytics", this::handle);
    }

    private void handle(String envelope) {
        try {
            JsonNode payload = objectMapper.readTree(envelope).path("payload");
            String tenantId = payload.path("tenantId").asText();
            String userId = payload.path("userId").asText();
            String activityLogId = payload.path("activityLogId").asText();
            OffsetDateTime occurredAt = OffsetDateTime.parse(payload.path("occurredAt").asText());
            ingestService.apply(tenantId, userId, activityLogId, occurredAt);

            EventEnvelope<Map<String, String>> updated = EventEnvelope.create(
                    "analytics.updated", tenantId, null, userId + ":" + activityLogId,
                    Map.of("userId", userId, "tenantId", tenantId));
            publisher.publish("analytics.updated", objectMapper.writeValueAsString(updated));
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
