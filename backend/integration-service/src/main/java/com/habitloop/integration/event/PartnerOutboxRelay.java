package com.habitloop.integration.event;

import com.habitloop.common.event.eventbridge.EventBridgePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnExpression("'${habitloop.events.transport:none}' == 'kafka' || '${habitloop.events.transport:none}' == 'eventbridge'")
public class PartnerOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(PartnerOutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectProvider<EventBridgePublisher> eventBridge;
    private final String transport;

    public PartnerOutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions, KafkaTemplate<String, String> kafka,
                              ObjectProvider<EventBridgePublisher> eventBridge,
                              @Value("${habitloop.events.transport:none}") String transport) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.kafka = kafka;
        this.eventBridge = eventBridge;
        this.transport = transport;
    }

    @Scheduled(fixedDelayString = "${habitloop.events.outbox-delay-ms:5000}")
    public void relay() {
        for (int i = 0; i < 50; i++) {
            if (!Boolean.TRUE.equals(transactions.execute(status -> sendOne()))) {
                return;
            }
        }
    }

    private boolean sendOne() {
        var rows = jdbc.query("""
                SELECT o.id, o.partition_key, o.envelope FROM partner_event_outbox o
                WHERE o.published_at IS NULL AND o.next_attempt_at <= CURRENT_TIMESTAMP
                  AND NOT EXISTS (
                    SELECT 1 FROM partner_event_outbox earlier
                    WHERE earlier.partition_key = o.partition_key AND earlier.published_at IS NULL
                      AND (earlier.created_at, earlier.id) < (o.created_at, o.id))
                ORDER BY o.created_at, o.id LIMIT 1 FOR UPDATE OF o SKIP LOCKED
                """, (rs, row) -> new OutboxRow(rs.getObject("id", UUID.class),
                rs.getString("partition_key"), rs.getString("envelope")));
        if (rows.isEmpty()) {
            return false;
        }
        OutboxRow row = rows.get(0);
        try {
            publish(row);
        } catch (RuntimeException e) {
            log.error("Partner outbox delivery failed for event {}; will retry", row.id(), e);
            String error = e.toString();
            jdbc.update("""
                    UPDATE partner_event_outbox SET attempts = attempts + 1,
                      next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds', last_error = ?
                    WHERE id = ?
                    """, error.substring(0, Math.min(error.length(), 500)), row.id());
            return true;
        }
        jdbc.update("""
                UPDATE partner_event_outbox SET published_at = CURRENT_TIMESTAMP, attempts = attempts + 1,
                  last_error = NULL WHERE id = ?
                """, row.id());
        return true;
    }

    private void publish(OutboxRow row) {
        if ("eventbridge".equals(transport)) {
            eventBridge.getObject().publish(PartnerEventPublisher.TOPIC, row.envelope());
            return;
        }
        try {
            kafka.send(PartnerEventPublisher.TOPIC, row.key(), row.envelope()).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing partner event", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Failed to publish partner event to Kafka", e);
        }
    }

    private record OutboxRow(UUID id, String key, String envelope) {
    }
}
