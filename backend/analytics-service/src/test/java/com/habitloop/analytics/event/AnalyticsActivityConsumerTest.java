package com.habitloop.analytics.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.analytics.event.AnalyticsActivityConsumer.InvalidActivityEventException;
import com.habitloop.analytics.service.AnalyticsIngestService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AnalyticsActivityConsumerTest {
    private final AnalyticsIngestService service = mock(AnalyticsIngestService.class);
    private final AnalyticsActivityConsumer consumer = new AnalyticsActivityConsumer(service, new ObjectMapper());

    @Test
    void malformedMessagesAreClassifiedForDeadLetter() {
        assertThatThrownBy(() -> consumer.onMessage("{"))
                .isInstanceOf(InvalidActivityEventException.class);
        assertThatThrownBy(() -> consumer.onMessage("{\"payload\":{\"occurredAt\":\"2026-01-01T00:00:00Z\"}}"))
                .isInstanceOf(InvalidActivityEventException.class);
        assertThatThrownBy(() -> consumer.onMessage("{\"payload\":{\"occurredAt\":\"invalid\"}}"))
                .isInstanceOf(InvalidActivityEventException.class);
        verifyNoInteractions(service);
    }

    @Test
    void transientFailuresRemainRetryable() {
        var time = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        doThrow(new IllegalStateException("store unavailable")).when(service).apply("t", "u", "a", time);
        assertThatThrownBy(() -> consumer.onMessage(
                "{\"payload\":{\"tenantId\":\"t\",\"userId\":\"u\",\"activityLogId\":\"a\","
                        + "\"occurredAt\":\"2026-01-01T00:00:00Z\"}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("store unavailable");
    }
}
