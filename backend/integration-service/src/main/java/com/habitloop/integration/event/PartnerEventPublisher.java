package com.habitloop.integration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.event.EventEnvelope;
import com.habitloop.common.event.eventbridge.EventBridgePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code partner.event.received}. The transport is chosen by {@code habitloop.events.transport}:
 * <ul>
 *   <li>{@code kafka} — local Docker Compose (the local stand-in for EventBridge)</li>
 *   <li>{@code eventbridge} — AWS; EventBridge then triggers the normalizer Lambda</li>
 *   <li>{@code none} — logs only (no broker), so the service still runs</li>
 * </ul>
 */
@Component
public class PartnerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PartnerEventPublisher.class);
    public static final String TOPIC = "partner.event.received";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String transport;
    private final ObjectProvider<EventBridgePublisher> eventBridge;

    public PartnerEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                 @Value("${habitloop.events.transport:none}") String transport,
                                 ObjectProvider<EventBridgePublisher> eventBridge) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.eventBridge = eventBridge;
    }

    public void publish(PartnerEventReceived event) {
        EventEnvelope<PartnerEventReceived> envelope = EventEnvelope.create(
                "partner.event.received", event.tenantId(), null,
                event.connectionId() + ":" + event.externalEventId(), event);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            switch (transport) {
                case "kafka" -> {
                    kafkaTemplate.send(TOPIC, event.connectionId(), json);
                    log.debug("Published partner.event.received (kafka) for connection {} event {}",
                            event.connectionId(), event.externalEventId());
                }
                case "eventbridge" -> {
                    EventBridgePublisher pub = eventBridge.getIfAvailable();
                    if (pub != null) {
                        pub.publish("partner.event.received", json);
                    } else {
                        log.error("transport=eventbridge but EventBridgePublisher is not available");
                    }
                }
                default -> log.info("[events disabled] partner.event.received: {}", json);
            }
        } catch (Exception e) {
            log.error("Failed to publish partner.event.received for event {}", event.externalEventId(), e);
        }
    }
}
