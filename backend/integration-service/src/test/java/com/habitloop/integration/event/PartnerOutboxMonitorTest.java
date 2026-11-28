package com.habitloop.integration.event;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartnerOutboxMonitorTest {
    @Test
    void warnsOnlyForOldPendingEvents() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L, 3L);
        Logger logger = (Logger) LoggerFactory.getLogger(PartnerOutboxMonitor.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var monitor = new PartnerOutboxMonitor(jdbc);
            monitor.monitor();
            assertThat(appender.list).isEmpty();
            monitor.monitor();
            assertThat(appender.list).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage()).contains("PARTNER_OUTBOX_BACKLOG", "3"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
