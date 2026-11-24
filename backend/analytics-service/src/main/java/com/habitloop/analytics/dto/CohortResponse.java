package com.habitloop.analytics.dto;

public record CohortResponse(
        String date,
        int activeUsers,
        int totalCompletions
) {
}
