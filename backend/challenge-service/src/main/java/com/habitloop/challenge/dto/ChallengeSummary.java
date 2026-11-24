package com.habitloop.challenge.dto;

import com.habitloop.challenge.entity.ChallengeStatus;
import com.habitloop.challenge.entity.Visibility;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ChallengeSummary(
        String id,
        String title,
        String description,
        Visibility visibility,
        ChallengeStatus status,
        BigDecimal target,
        LocalDate startsOn,
        LocalDate endsOn
) {
}
