package com.habitloop.activity.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${habitloop.events.transport:none}' == 'kafka' || '${habitloop.events.transport:none}' == 'eventbridge'")
public class ActivityOutboxMonitor {
    private static final Logger log = LoggerFactory.getLogger(ActivityOutboxMonitor.class);
    private final JdbcTemplate jdbc;

    public ActivityOutboxMonitor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${habitloop.events.outbox-monitor-delay-ms:60000}")
    public void monitor() {
        long count = jdbc.queryForObject("""
                SELECT count(*) FROM activity_outbox
                WHERE published_at IS NULL AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
                """, Long.class);
        if (count > 0) {
            log.warn("ACTIVITY_OUTBOX_BACKLOG pending_over_five_minutes={}", count);
        }
    }
}
