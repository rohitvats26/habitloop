package com.habitloop.challenge.dto;

import com.habitloop.challenge.entity.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateChallengeRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 1000) String description,
        @NotNull Visibility visibility,
        @NotBlank @Size(max = 40) String metric,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal target,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn
) {
    @jakarta.validation.constraints.AssertTrue(message = "endsOn must not be before startsOn")
    public boolean isDateRangeValid() {
        return startsOn == null || endsOn == null || !endsOn.isBefore(startsOn);
    }
}
