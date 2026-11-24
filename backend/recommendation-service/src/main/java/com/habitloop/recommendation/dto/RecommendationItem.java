package com.habitloop.recommendation.dto;

import java.util.Map;

public record RecommendationItem(
        String type,            // TIMING, FREQUENCY, CONSISTENCY
        String habitId,         // nullable — user-level when null
        String title,
        String message,
        Map<String, Object> action,
        double confidence
) {
}
