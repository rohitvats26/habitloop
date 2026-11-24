package com.habitloop.recommendation.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.recommendation.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Evicts a user's cached recommendations when their analytics change, so fresh activity is reflected
 * before the TTL expires. Active only when {@code habitloop.kafka.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.kafka.enabled", havingValue = "true")
public class AnalyticsUpdatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsUpdatedConsumer.class);

    private final RecommendationService recommendationService;
    private final ObjectMapper objectMapper;

    public AnalyticsUpdatedConsumer(RecommendationService recommendationService, ObjectMapper objectMapper) {
        this.recommendationService = recommendationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "analytics.updated", groupId = "recommendation-service")
    public void onMessage(String message) {
        try {
            JsonNode payload = objectMapper.readTree(message).path("payload");
            String userId = payload.path("userId").asText();
            if (!userId.isBlank()) {
                recommendationService.evict(userId);
            }
        } catch (Exception e) {
            log.error("Failed to process analytics.updated message", e);
        }
    }
}
