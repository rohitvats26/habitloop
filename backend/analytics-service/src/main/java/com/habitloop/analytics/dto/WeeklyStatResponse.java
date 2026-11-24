package com.habitloop.analytics.dto;

public record WeeklyStatResponse(
        String week,
        int completions
) {
}
