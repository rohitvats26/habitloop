package com.habitloop.challenge.dto;

import java.math.BigDecimal;

public record LeaderboardEntry(
        String userId,
        BigDecimal progress,
        boolean completed
) {
}
