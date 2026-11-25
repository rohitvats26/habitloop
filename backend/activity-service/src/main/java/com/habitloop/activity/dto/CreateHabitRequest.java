package com.habitloop.activity.dto;

import com.habitloop.activity.entity.Frequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateHabitRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull Frequency frequency,
        Map<String, Object> schedule,
        @Positive int targetPerPeriod
) {
    @jakarta.validation.constraints.AssertTrue(message = "schedule is invalid for the selected frequency")
    public boolean isScheduleValid() {
        return HabitScheduleRules.isCompatible(schedule, frequency);
    }
}
