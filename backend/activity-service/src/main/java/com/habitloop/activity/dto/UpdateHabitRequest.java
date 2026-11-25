package com.habitloop.activity.dto;

import com.habitloop.activity.entity.Frequency;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateHabitRequest(
        @Size(max = 120) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String name,
        @Size(max = 500) String description,
        Frequency frequency,
        Map<String, Object> schedule,
        @Positive Integer targetPerPeriod,
        Boolean active
) {
    @AssertTrue(message = "provide at least one habit field to update")
    public boolean isUpdatePresent() {
        return name != null || description != null || frequency != null || schedule != null
                || targetPerPeriod != null || active != null;
    }

    @AssertTrue(message = "schedule contains unsupported values")
    public boolean isScheduleValid() {
        return schedule == null || HabitScheduleRules.isValid(schedule);
    }
}
