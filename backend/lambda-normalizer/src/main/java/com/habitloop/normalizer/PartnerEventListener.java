package com.habitloop.normalizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

/**
 * Local stand-in for the AWS Lambda: consumes {@code partner.event.received} and publishes
 * {@code partner.activity.normalized}. The same {@link EventNormalizer} logic is reused by the Lambda
 * handler in AWS (docs/06 §6, docs/07).
 */
@Component
public class PartnerEventListener {

    private static final Logger log = LoggerFactory.getLogger(PartnerEventListener.class);
    private static final String OUT_TOPIC = "partner.activity.normalized";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventNormalizer normalizer;
    private final ObjectMapper mapper;

    public PartnerEventListener(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
        this.normalizer = new EventNormalizer(mapper);
    }

    @KafkaListener(topics = "partner.event.received", groupId = "lambda-normalizer")
    public void onMessage(String message) {
        String normalized;
        try {
            normalized = normalizer.normalize(message);
            if (normalized == null) {
                throw new InvalidPartnerEventException("Invalid partner event");
            }
        } catch (JsonProcessingException | DateTimeParseException e) {
            throw new InvalidPartnerEventException("Invalid partner event", e);
        }
        try {
            var payload = mapper.readTree(normalized).path("payload");
            String key = payload.path("tenantId").asText() + ":" + payload.path("userId").asText();
            kafkaTemplate.send(OUT_TOPIC, key, normalized).get(10, TimeUnit.SECONDS);
            log.info("Normalized partner event -> {}", OUT_TOPIC);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing normalized activity", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | JsonProcessingException e) {
            throw new IllegalStateException("Failed to publish normalized activity", e);
        }
    }

    public static class InvalidPartnerEventException extends RuntimeException {
        public InvalidPartnerEventException(String message) {
            super(message);
        }

        public InvalidPartnerEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
