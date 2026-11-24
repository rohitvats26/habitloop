package com.habitloop.activity.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.event.EventEnvelope;
import com.habitloop.common.event.eventbridge.EventBridgePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * EventBridge counterpart of {@link KafkaActivityPublisher}: publishes {@code activity.logged} after the
 * DB transaction commits. Active only when {@code habitloop.events.transport=eventbridge}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "eventbridge")
public class EventBridgeActivityPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventBridgeActivityPublisher.class);

    private final EventBridgePublisher publisher;
    private final ObjectMapper objectMapper;

    public EventBridgeActivityPublisher(EventBridgePublisher publisher, ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityLogged(ActivityLoggedEvent event) {
        try {
            EventEnvelope<ActivityLoggedEvent> envelope = EventEnvelope.create(
                    "activity.logged", event.tenantId(), null, event.idempotencyKey(), event);
            publisher.publish("activity.logged", objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("Failed to publish activity.logged to EventBridge for log {}", event.activityLogId(), e);
        }
    }
}
