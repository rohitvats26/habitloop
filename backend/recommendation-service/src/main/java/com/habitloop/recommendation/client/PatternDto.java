package com.habitloop.recommendation.client;

import java.util.Map;

/** Mirrors analytics-service /api/analytics/pattern. */
public record PatternDto(
        Map<String, Integer> byHour,
        Map<String, Integer> byWeekday
) {
    public static PatternDto empty() {
        return new PatternDto(Map.of(), Map.of());
    }
}
