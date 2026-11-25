package com.habitloop.analytics.service;

import com.habitloop.analytics.store.AnalyticsItem;
import com.habitloop.analytics.store.AnalyticsKeys;
import com.habitloop.analytics.store.AnalyticsStore;
import com.habitloop.common.time.TimeZones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;

/**
 * Updates the aggregated analytics for a user when an activity is logged. Idempotent per activityLogId via
 * a PROCESSED marker, so replays never inflate counts (NFR-2). Writes daily, weekly and pattern-summary
 * items, plus GSI1 attributes for tenant-wide cohort queries (docs/04 §5, docs/07).
 */
@Service
public class AnalyticsIngestService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsIngestService.class);

    private final AnalyticsStore store;

    public AnalyticsIngestService(AnalyticsStore store) {
        this.store = store;
    }

    public void apply(String tenantId, String userId, String activityLogId, OffsetDateTime occurredAt) {
        String pk = AnalyticsKeys.pk(tenantId, userId);
        if (!store.markProcessedIfAbsent(pk, activityLogId)) {
            log.debug("Activity {} already aggregated — skipping", activityLogId);
            return;
        }

        ZonedDateTime indiaOccurredAt = occurredAt.atZoneSameInstant(TimeZones.INDIA);
        LocalDate date = indiaOccurredAt.toLocalDate();
        String hour = String.format("%02d", indiaOccurredAt.getHour());
        String weekday = date.getDayOfWeek().name().substring(0, 3); // MON, TUE, ...
        String isoWeek = String.format("%d-W%02d",
                date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        String now = OffsetDateTime.now().toString();

        AnalyticsItem daily = getOrNew(pk, AnalyticsKeys.skDaily(date));
        daily.setCompletions(daily.getCompletions() + 1);
        AnalyticsItem.bump(daily.getByHour(), hour);
        AnalyticsItem.bump(daily.getByWeekday(), weekday);
        daily.setGsi1pk(AnalyticsKeys.gsi1pk(tenantId, date));
        daily.setGsi1sk(AnalyticsKeys.gsi1sk(userId));
        daily.setUpdatedAt(now);
        store.put(daily);

        AnalyticsItem weekly = getOrNew(pk, AnalyticsKeys.skWeekly(isoWeek));
        weekly.setCompletions(weekly.getCompletions() + 1);
        weekly.setUpdatedAt(now);
        store.put(weekly);

        AnalyticsItem pattern = getOrNew(pk, AnalyticsKeys.SK_PATTERN);
        AnalyticsItem.bump(pattern.getByHour(), hour);
        AnalyticsItem.bump(pattern.getByWeekday(), weekday);
        pattern.setUpdatedAt(now);
        store.put(pattern);

        log.debug("Aggregated activity {} for user {} on {}", activityLogId, userId, date);
    }

    private AnalyticsItem getOrNew(String pk, String sk) {
        return store.get(pk, sk).orElseGet(() -> {
            AnalyticsItem item = new AnalyticsItem();
            item.setPk(pk);
            item.setSk(sk);
            return item;
        });
    }
}
