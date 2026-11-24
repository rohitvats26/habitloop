package com.habitloop.analytics.service;

import com.habitloop.analytics.dto.PatternSummaryResponse;
import com.habitloop.analytics.dto.SummaryResponse;
import com.habitloop.analytics.store.InMemoryAnalyticsStore;
import com.habitloop.common.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsQueryServiceTest {

    private final String tenantId = "t1";
    private final String userId = "u9";
    private AnalyticsQueryService query;

    @BeforeEach
    void setUp() {
        InMemoryAnalyticsStore store = new InMemoryAnalyticsStore();
        AnalyticsIngestService ingest = new AnalyticsIngestService(store);
        // Seed two completions "now" so today/this-week queries match.
        OffsetDateTime now = OffsetDateTime.now();
        ingest.apply(tenantId, userId, "log1", now);
        ingest.apply(tenantId, userId, "log2", now);

        query = new AnalyticsQueryService(store);
        var principal = new AuthUser(userId, tenantId, List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void summary_reportsTodayAndWeek() {
        SummaryResponse summary = query.summary();
        assertThat(summary.completedToday()).isEqualTo(2);
        assertThat(summary.weekCompletions()).isEqualTo(2);
        assertThat(summary.trend()).isNotEmpty();
    }

    @Test
    void pattern_reflectsSeededActivity() {
        PatternSummaryResponse pattern = query.pattern();
        int total = pattern.byHour().values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(2);
    }
}
