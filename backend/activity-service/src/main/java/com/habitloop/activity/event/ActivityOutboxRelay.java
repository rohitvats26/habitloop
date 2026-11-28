package com.habitloop.activity.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Component
@ConditionalOnExpression("'${habitloop.events.transport:none}' == 'kafka' || '${habitloop.events.transport:none}' == 'eventbridge'")
public class ActivityOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(ActivityOutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ActivityEventTransport transport;

    public ActivityOutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions, ActivityEventTransport transport) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.transport = transport;
    }

    @Scheduled(fixedDelayString = "${habitloop.events.outbox-delay-ms:5000}")
    public void relay() {
        for (int i = 0; i < 50; i++) {
            Boolean processed = transactions.execute(status -> sendOne());
            if (!Boolean.TRUE.equals(processed)) {
                return;
            }
        }
    }

    private boolean sendOne() {
        var rows = jdbc.query("""
                SELECT o.id, o.partition_key, o.envelope FROM activity_outbox o
                WHERE o.published_at IS NULL AND o.next_attempt_at <= CURRENT_TIMESTAMP
                  AND NOT EXISTS (
                    SELECT 1 FROM activity_outbox earlier
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
            transport.publish(row.key(), row.envelope());
        } catch (RuntimeException e) {
            log.error("Activity outbox delivery failed for event {}; will retry", row.id(), e);
            String error = e.toString();
            jdbc.update("""
                    UPDATE activity_outbox SET attempts = attempts + 1,
                      next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds', last_error = ?
                    WHERE id = ?
                    """, error.substring(0, Math.min(error.length(), 500)), row.id());
            return true;
        }
        jdbc.update("""
                UPDATE activity_outbox SET published_at = CURRENT_TIMESTAMP, attempts = attempts + 1,
                  last_error = NULL WHERE id = ?
                """, row.id());
        return true;
    }

    private record OutboxRow(UUID id, String key, String envelope) {
    }
}
