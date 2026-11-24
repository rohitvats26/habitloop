package com.habitloop.integration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.event.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code partner.event.received}. When {@code habitloop.kafka.enabled=true} it sends to Kafka
 * (the local stand-in for EventBridge); otherwise it logs, so the service runs without a broker. In AWS
 * this is replaced/augmented by an EventBridge PutEvents call.
 */
@Component
public class PartnerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PartnerEventPublisher.class);
    public static final String TOPIC = "partner.event.received";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public PartnerEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                 @Value("${habitloop.kafka.enabled:false}") boolean enabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void publish(PartnerEventReceived event) {
        EventEnvelope<PartnerEventReceived> envelope = EventEnvelope.create(
                "partner.event.received", event.tenantId(), null,
                event.connectionId() + ":" + event.externalEventId(), event);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            if (enabled) {
                kafkaTemplate.send(TOPIC, event.connectionId(), json);
                log.debug("Published partner.event.received for connection {} event {}",
                        event.connectionId(), event.externalEventId());
            } else {
                log.info("[kafka disabled] partner.event.received: {}", json);
            }
        } catch (Exception e) {
            log.error("Failed to publish partner.event.received for event {}", event.externalEventId(), e);
        }
    }
}
