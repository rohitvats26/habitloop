package com.habitloop.normalizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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

    public PartnerEventListener(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.normalizer = new EventNormalizer(mapper);
    }

    @KafkaListener(topics = "partner.event.received", groupId = "lambda-normalizer")
    public void onMessage(String message) {
        try {
            String normalized = normalizer.normalize(message);
            if (normalized == null) {
                log.warn("Dropping invalid partner event (would go to DLQ in AWS)");
                return;
            }
            kafkaTemplate.send(OUT_TOPIC, normalized);
            log.info("Normalized partner event -> {}", OUT_TOPIC);
        } catch (Exception e) {
            log.error("Failed to normalize partner event", e);
        }
    }
}
