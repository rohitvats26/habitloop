package com.habitloop.challenge.dto;

import com.habitloop.challenge.entity.ChallengeStatus;
import com.habitloop.challenge.entity.Visibility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ChallengeDetailResponse(
        String id,
        String title,
        String description,
        Visibility visibility,
        ChallengeStatus status,
        BigDecimal target,
        LocalDate startsOn,
        LocalDate endsOn,
        BigDecimal myProgress,
        boolean completed,
        boolean canInvite,
        List<LeaderboardEntry> leaderboard
) {
}
