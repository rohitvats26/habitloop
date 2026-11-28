package com.habitloop.challenge.dto;

import java.math.BigDecimal;

public record LeaderboardEntry(
        String userId,
        String displayName,
        BigDecimal progress,
        boolean completed
) {
}
