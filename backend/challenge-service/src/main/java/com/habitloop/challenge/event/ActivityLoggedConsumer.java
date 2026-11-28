package com.habitloop.challenge.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code activity.logged} and updates challenge progress. Challenge progress is driven entirely
 * by events — no synchronous call to activity-service (NFR-9). Active only when
 * {@code habitloop.kafka.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "kafka")
public class ActivityLoggedConsumer {

    private final ActivityEventHandler handler;

    public ActivityLoggedConsumer(ActivityEventHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = "activity.logged", groupId = "challenge-service")
    public void onMessage(String message) {
        handler.handle(message);
    }
}
