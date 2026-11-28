package com.habitloop.analytics.store;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AnalyticsUpdate(String tenantId, String userId, String activityLogId, LocalDate date,
                              String isoWeek, String hour, String weekday) {
    public String pk() {
        return AnalyticsKeys.pk(tenantId, userId);
    }

    public AnalyticsItem daily(AnalyticsItem previous) {
        AnalyticsItem item = copy(previous, AnalyticsKeys.skDaily(date));
        item.setCompletions(item.getCompletions() + 1);
        AnalyticsItem.bump(item.getByHour(), hour);
        AnalyticsItem.bump(item.getByWeekday(), weekday);
        item.setGsi1pk(AnalyticsKeys.gsi1pk(tenantId, date));
        item.setGsi1sk(AnalyticsKeys.gsi1sk(userId));
        return item;
    }

    public AnalyticsItem weekly(AnalyticsItem previous) {
        AnalyticsItem item = copy(previous, AnalyticsKeys.skWeekly(isoWeek));
        item.setCompletions(item.getCompletions() + 1);
        return item;
    }

    public AnalyticsItem pattern(AnalyticsItem previous) {
        AnalyticsItem item = copy(previous, AnalyticsKeys.SK_PATTERN);
        AnalyticsItem.bump(item.getByHour(), hour);
        AnalyticsItem.bump(item.getByWeekday(), weekday);
        return item;
    }

    private AnalyticsItem copy(AnalyticsItem previous, String sk) {
        AnalyticsItem item = previous == null ? new AnalyticsItem() : previous.copy();
        item.setPk(pk());
        item.setSk(sk);
        item.setUpdatedAt(OffsetDateTime.now().toString());
        item.setRevision(UUID.randomUUID().toString());
        return item;
    }
}
