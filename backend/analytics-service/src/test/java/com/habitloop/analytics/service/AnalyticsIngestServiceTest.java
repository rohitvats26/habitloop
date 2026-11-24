package com.habitloop.analytics.service;

import com.habitloop.analytics.store.AnalyticsItem;
import com.habitloop.analytics.store.AnalyticsKeys;
import com.habitloop.analytics.store.InMemoryAnalyticsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsIngestServiceTest {

    private final String tenantId = "t1";
    private final String userId = "u9";
    private InMemoryAnalyticsStore store;
    private AnalyticsIngestService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryAnalyticsStore();
        service = new AnalyticsIngestService(store);
    }

    @Test
    void apply_aggregatesDailyWeeklyAndPattern() {
        OffsetDateTime at = OffsetDateTime.parse("2026-11-23T07:30:00Z");
        service.apply(tenantId, userId, "log1", at);
        service.apply(tenantId, userId, "log2", at);

        String pk = AnalyticsKeys.pk(tenantId, userId);
        AnalyticsItem daily = store.get(pk, AnalyticsKeys.skDaily(LocalDate.of(2026, 11, 23))).orElseThrow();
        assertThat(daily.getCompletions()).isEqualTo(2);
        assertThat(daily.getByHour()).containsEntry("07", 2);
        assertThat(daily.getGsi1pk()).isEqualTo(AnalyticsKeys.gsi1pk(tenantId, LocalDate.of(2026, 11, 23)));

        AnalyticsItem pattern = store.get(pk, AnalyticsKeys.SK_PATTERN).orElseThrow();
        assertThat(pattern.getByHour()).containsEntry("07", 2);
    }

    @Test
    void apply_isIdempotentPerActivityLog() {
        OffsetDateTime at = OffsetDateTime.parse("2026-11-23T07:30:00Z");
        service.apply(tenantId, userId, "log1", at);
        service.apply(tenantId, userId, "log1", at); // replay

        String pk = AnalyticsKeys.pk(tenantId, userId);
        AnalyticsItem daily = store.get(pk, AnalyticsKeys.skDaily(LocalDate.of(2026, 11, 23))).orElseThrow();
        assertThat(daily.getCompletions()).isEqualTo(1);
    }
}
