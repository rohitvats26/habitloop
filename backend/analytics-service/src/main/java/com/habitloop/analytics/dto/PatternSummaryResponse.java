package com.habitloop.analytics.dto;

import java.util.Map;

/** Aggregated activity patterns consumed by recommendation-service and the UI. */
public record PatternSummaryResponse(
        Map<String, Integer> byHour,
        Map<String, Integer> byWeekday
) {
}
