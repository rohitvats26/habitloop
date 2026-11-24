package com.habitloop.analytics.dto;

import java.util.List;

public record SummaryResponse(
        int completedToday,
        int weekCompletions,
        List<WeeklyStatResponse> trend
) {
}
