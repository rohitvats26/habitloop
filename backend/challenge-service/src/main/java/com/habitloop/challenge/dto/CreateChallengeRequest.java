package com.habitloop.challenge.dto;

import com.habitloop.challenge.entity.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateChallengeRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 1000) String description,
        @NotNull Visibility visibility,
        @NotBlank @Size(max = 40) String metric,
        @NotNull @Positive BigDecimal target,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn
) {
}
