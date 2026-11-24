package com.habitloop.activity.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.event.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes {@code activity.logged} to Kafka only after the database transaction commits, so an event is
 * never emitted for a change that rolled back (reliable publishing, docs/07). Active only when
 * {@code habitloop.kafka.enabled=true}; in tests/local-without-broker the in-process event is still
 * raised and simply not forwarded.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "kafka")
public class KafkaActivityPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaActivityPublisher.class);
    public static final String TOPIC = "activity.logged";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaActivityPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityLogged(ActivityLoggedEvent event) {
        try {
            EventEnvelope<ActivityLoggedEvent> envelope = EventEnvelope.create(
                    "activity.logged", event.tenantId(), null, event.idempotencyKey(), event);
            String key = event.tenantId() + ":" + event.userId();
            kafkaTemplate.send(TOPIC, key, objectMapper.writeValueAsString(envelope));
            log.debug("Published activity.logged for user {} key {}", event.userId(), event.idempotencyKey());
        } catch (Exception e) {
            log.error("Failed to publish activity.logged for log {}", event.activityLogId(), e);
        }
    }
}
