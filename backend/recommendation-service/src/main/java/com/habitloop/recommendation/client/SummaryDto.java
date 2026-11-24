package com.habitloop.recommendation.client;

import java.util.List;

/** Mirrors analytics-service /api/analytics/summary. */
public record SummaryDto(
        int completedToday,
        int weekCompletions,
        List<WeekPoint> trend
) {
    public record WeekPoint(String week, int completions) {
    }

    public static SummaryDto empty() {
        return new SummaryDto(0, 0, List.of());
    }
}
