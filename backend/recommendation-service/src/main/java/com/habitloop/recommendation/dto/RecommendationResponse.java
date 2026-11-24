package com.habitloop.recommendation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RecommendationResponse(
        OffsetDateTime generatedAt,
        List<RecommendationItem> items
) {
    public static RecommendationResponse of(List<RecommendationItem> items) {
        return new RecommendationResponse(OffsetDateTime.now(), items);
    }
}
