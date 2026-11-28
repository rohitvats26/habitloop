package com.habitloop.analytics.service;

import com.habitloop.analytics.store.AnalyticsItem;
import com.habitloop.analytics.store.AnalyticsKeys;
import com.habitloop.analytics.store.InMemoryAnalyticsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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
        OffsetDateTime at = OffsetDateTime.parse("2026-11-23T22:30:00Z");
        service.apply(tenantId, userId, "log1", at);
        service.apply(tenantId, userId, "log2", at);

        String pk = AnalyticsKeys.pk(tenantId, userId);
        AnalyticsItem daily = store.get(pk, AnalyticsKeys.skDaily(LocalDate.of(2026, 11, 24))).orElseThrow();
        assertThat(daily.getCompletions()).isEqualTo(2);
        assertThat(daily.getByHour()).containsEntry("04", 2);
        assertThat(daily.getByWeekday()).containsEntry("TUE", 2);
        assertThat(daily.getGsi1pk()).isEqualTo(AnalyticsKeys.gsi1pk(tenantId, LocalDate.of(2026, 11, 24)));

        AnalyticsItem pattern = store.get(pk, AnalyticsKeys.SK_PATTERN).orElseThrow();
        assertThat(pattern.getByHour()).containsEntry("04", 2);
    }

    @Test
    void apply_isIdempotentPerActivityLog() {
        OffsetDateTime at = OffsetDateTime.parse("2026-11-23T22:30:00Z");
        service.apply(tenantId, userId, "log1", at);
        service.apply(tenantId, userId, "log1", at); // replay

        String pk = AnalyticsKeys.pk(tenantId, userId);
        AnalyticsItem daily = store.get(pk, AnalyticsKeys.skDaily(LocalDate.of(2026, 11, 24))).orElseThrow();
        assertThat(daily.getCompletions()).isEqualTo(1);
    }

    @Test
    void concurrentEventsAndReplaysCountExactlyOnce() throws Exception {
        OffsetDateTime at = OffsetDateTime.parse("2026-11-23T22:30:00Z");
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = IntStream.range(0, 400)
                    .<java.util.concurrent.Callable<Void>>mapToObj(i -> () -> {
                        service.apply(tenantId, userId, "log" + (i % 200), at);
                        return null;
                    }).toList();
            for (var future : executor.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        String pk = AnalyticsKeys.pk(tenantId, userId);
        assertThat(store.get(pk, AnalyticsKeys.skDaily(LocalDate.of(2026, 11, 24)))
                .orElseThrow().getCompletions()).isEqualTo(200);
        assertThat(store.get(pk, AnalyticsKeys.SK_PATTERN).orElseThrow().getByHour()).containsEntry("04", 200);
    }
}
