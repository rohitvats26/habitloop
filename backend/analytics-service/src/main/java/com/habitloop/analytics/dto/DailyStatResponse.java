package com.habitloop.analytics.dto;

import java.util.Map;

public record DailyStatResponse(
        String date,
        int completions,
        Map<String, Integer> byHour,
        Map<String, Integer> byWeekday
) {
}
