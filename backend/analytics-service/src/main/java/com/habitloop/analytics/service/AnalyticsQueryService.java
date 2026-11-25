package com.habitloop.analytics.service;

import com.habitloop.analytics.dto.CohortResponse;
import com.habitloop.analytics.dto.DailyStatResponse;
import com.habitloop.analytics.dto.PatternSummaryResponse;
import com.habitloop.analytics.dto.SummaryResponse;
import com.habitloop.analytics.dto.WeeklyStatResponse;
import com.habitloop.analytics.store.AnalyticsItem;
import com.habitloop.analytics.store.AnalyticsKeys;
import com.habitloop.analytics.store.AnalyticsStore;
import com.habitloop.common.exception.InvalidRequestException;
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.common.time.TimeZones;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsQueryService {

    private static final int DEFAULT_TREND_WEEKS = 8;

    private final AnalyticsStore store;

    public AnalyticsQueryService(AnalyticsStore store) {
        this.store = store;
    }

    public SummaryResponse summary() {
        String pk = pk();
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        int completedToday = store.get(pk, AnalyticsKeys.skDaily(today))
                .map(AnalyticsItem::getCompletions).orElse(0);

        String currentWeek = isoWeek(today);
        int weekCompletions = store.get(pk, AnalyticsKeys.skWeekly(currentWeek))
                .map(AnalyticsItem::getCompletions).orElse(0);

        List<WeeklyStatResponse> trend = weeklyTrend(pk, DEFAULT_TREND_WEEKS);
        return new SummaryResponse(completedToday, weekCompletions, trend);
    }

    public List<DailyStatResponse> daily(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("from must not be after to");
        }
        String pk = pk();
        return store.queryPrefix(pk, AnalyticsKeys.SK_DAILY_PREFIX).stream()
                .map(item -> new DailyStatResponse(stripPrefix(item.getSk(), AnalyticsKeys.SK_DAILY_PREFIX),
                        item.getCompletions(), item.getByHour(), item.getByWeekday()))
                .filter(d -> inRange(LocalDate.parse(d.date()), from, to))
                .sorted(Comparator.comparing(DailyStatResponse::date))
                .toList();
    }

    public List<WeeklyStatResponse> weekly(int weeks) {
        if (weeks < 1 || weeks > 52) {
            throw new InvalidRequestException("weeks must be between 1 and 52");
        }
        return weeklyTrend(pk(), weeks);
    }

    public PatternSummaryResponse pattern() {
        return store.get(pk(), AnalyticsKeys.SK_PATTERN)
                .map(item -> new PatternSummaryResponse(item.getByHour(), item.getByWeekday()))
                .orElseGet(() -> new PatternSummaryResponse(Map.of(), Map.of()));
    }

    public CohortResponse cohort(LocalDate date) {
        String tenantId = SecurityUtils.currentTenantId();
        List<AnalyticsItem> items = store.queryCohort(AnalyticsKeys.gsi1pk(tenantId, date));
        int totalCompletions = items.stream().mapToInt(AnalyticsItem::getCompletions).sum();
        return new CohortResponse(date.toString(), items.size(), totalCompletions);
    }

    private List<WeeklyStatResponse> weeklyTrend(String pk, int weeks) {
        return store.queryPrefix(pk, AnalyticsKeys.SK_WEEKLY_PREFIX).stream()
                .map(item -> new WeeklyStatResponse(
                        stripPrefix(item.getSk(), AnalyticsKeys.SK_WEEKLY_PREFIX), item.getCompletions()))
                .sorted(Comparator.comparing(WeeklyStatResponse::week))
                .skip(Math.max(0, countWeekly(pk) - weeks))
                .toList();
    }

    private long countWeekly(String pk) {
        return store.queryPrefix(pk, AnalyticsKeys.SK_WEEKLY_PREFIX).size();
    }

    private boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
    }

    private String isoWeek(LocalDate date) {
        return String.format("%d-W%02d",
                date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private String stripPrefix(String sk, String prefix) {
        return sk.substring(prefix.length());
    }

    private String pk() {
        return AnalyticsKeys.pk(SecurityUtils.currentTenantId(), SecurityUtils.currentUserId());
    }
}
