package com.habitloop.activity.event;

import com.habitloop.activity.entity.ActivitySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Payload produced by the partner-event normalizer Lambda and delivered on the
 * {@code partner.activity.normalized} topic (see docs/07). Turned into an idempotent activity log.
 */
public record NormalizedPartnerActivity(
        String tenantId,
        String userId,
        String habitId,
        String activityType,
        BigDecimal value,
        String unit,
        OffsetDateTime occurredAt,
        String externalEventId,
        ActivitySource source
) {
}
