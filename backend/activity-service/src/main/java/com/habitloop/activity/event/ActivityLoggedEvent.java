package com.habitloop.activity.event;

import com.habitloop.activity.entity.ActivitySource;

import java.time.OffsetDateTime;

/**
 * Internal domain event published when an activity is logged (manual or ingested). Consumed in-process
 * by the transactional outbox and, downstream, by challenge- and analytics-service.
 */
public record ActivityLoggedEvent(
        String tenantId,
        String userId,
        String habitId,
        String activityLogId,
        String activityType,
        OffsetDateTime occurredAt,
        ActivitySource source,
        String externalEventId
) {
    public String idempotencyKey() {
        return tenantId + ":" + userId + ":" + source + ":" + (externalEventId == null ? activityLogId : externalEventId);
    }
}
