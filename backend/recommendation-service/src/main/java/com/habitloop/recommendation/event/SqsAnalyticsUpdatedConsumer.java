package com.habitloop.recommendation.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.event.eventbridge.SqsEventPoller;
import com.habitloop.recommendation.service.RecommendationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * EventBridge counterpart of {@link AnalyticsUpdatedConsumer}: polls this service's SQS queue (fed by the
 * {@code analytics.updated} rule) and evicts the user's cached recommendations. Active only when
 * {@code habitloop.events.transport=eventbridge}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "eventbridge")
public class SqsAnalyticsUpdatedConsumer {

    private final RecommendationService recommendationService;
    private final ObjectMapper objectMapper;
    private final SqsEventPoller poller;

    public SqsAnalyticsUpdatedConsumer(SqsClient sqs, ObjectMapper objectMapper,
                                       RecommendationService recommendationService,
                                       @Value("${habitloop.events.queue-url:}") String queueUrl) {
        this.recommendationService = recommendationService;
        this.objectMapper = objectMapper;
        this.poller = new SqsEventPoller(sqs, objectMapper, queueUrl, "recommendation", this::handle);
    }

    private void handle(String envelope) {
        try {
            JsonNode payload = objectMapper.readTree(envelope).path("payload");
            String userId = payload.path("userId").asText();
            if (!userId.isBlank()) {
                recommendationService.evict(userId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process analytics.updated", e);
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
