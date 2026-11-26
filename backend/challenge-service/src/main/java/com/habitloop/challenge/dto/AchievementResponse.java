package com.habitloop.challenge.dto;

import java.time.OffsetDateTime;

public record AchievementResponse(
        String id,
        String code,
        String title,
        String description,
        String sourceId,
        OffsetDateTime awardedAt
) {
}
