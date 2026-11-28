package com.habitloop.analytics.service;

import com.habitloop.analytics.store.AnalyticsStore;
import com.habitloop.analytics.store.AnalyticsUpdate;
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
        ZonedDateTime indiaOccurredAt = occurredAt.atZoneSameInstant(TimeZones.INDIA);
        LocalDate date = indiaOccurredAt.toLocalDate();
        String hour = String.format("%02d", indiaOccurredAt.getHour());
        String weekday = date.getDayOfWeek().name().substring(0, 3); // MON, TUE, ...
        String isoWeek = String.format("%d-W%02d",
                date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        if (!store.apply(new AnalyticsUpdate(tenantId, userId, activityLogId, date, isoWeek, hour, weekday))) {
            log.debug("Activity {} already aggregated — skipping", activityLogId);
            return;
        }

        log.debug("Aggregated activity {} for user {} on {}", activityLogId, userId, date);
    }

}
