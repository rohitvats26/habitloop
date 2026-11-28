package com.habitloop.activity.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code partner.activity.normalized} (the Lambda's output) and turns each event into an
 * idempotent activity log. At-least-once delivery + the activity_log unique key = effectively-once,
 * so replays never create duplicate logs (NFR-2). Active only when {@code habitloop.kafka.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "kafka")
public class PartnerActivityConsumer {

    private final PartnerActivityHandler handler;

    public PartnerActivityConsumer(PartnerActivityHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = "partner.activity.normalized", groupId = "activity-service")
    public void onMessage(String message) {
        handler.handle(message);
    }
}
