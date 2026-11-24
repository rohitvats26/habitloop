package com.habitloop.activity.dto;

import com.habitloop.activity.entity.ActivitySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ActivityLogResponse(
        String id,
        String habitId,
        String activityType,
        BigDecimal value,
        String unit,
        OffsetDateTime occurredAt,
        ActivitySource source
) {
}
