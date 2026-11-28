package com.habitloop.integration.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${habitloop.events.transport:none}' == 'kafka' || '${habitloop.events.transport:none}' == 'eventbridge'")
public class PartnerOutboxMonitor {
    private static final Logger log = LoggerFactory.getLogger(PartnerOutboxMonitor.class);
    private final JdbcTemplate jdbc;

    public PartnerOutboxMonitor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${habitloop.events.outbox-monitor-delay-ms:60000}")
    public void monitor() {
        long count = jdbc.queryForObject("""
                SELECT count(*) FROM partner_event_outbox
                WHERE published_at IS NULL AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
                """, Long.class);
        if (count > 0) {
            log.warn("PARTNER_OUTBOX_BACKLOG pending_over_five_minutes={}", count);
        }
    }
}
