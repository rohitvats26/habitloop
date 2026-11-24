package com.habitloop.activity.dto;

import com.habitloop.activity.entity.Frequency;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateHabitRequest(
        @Size(max = 120) String name,
        @Size(max = 500) String description,
        Frequency frequency,
        Map<String, Object> schedule,
        Integer targetPerPeriod,
        Boolean active
) {
}
