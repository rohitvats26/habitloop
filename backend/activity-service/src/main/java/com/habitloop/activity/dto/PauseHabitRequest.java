package com.habitloop.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PauseHabitRequest(
        @NotNull @Min(1) @Max(90) Integer days
) {
}
