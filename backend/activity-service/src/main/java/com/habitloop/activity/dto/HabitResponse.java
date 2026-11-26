package com.habitloop.activity.dto;

import com.habitloop.activity.entity.Frequency;

import java.time.LocalDate;
import java.util.Map;

public record HabitResponse(
        String id,
        String name,
        String description,
        Frequency frequency,
        Map<String, Object> schedule,
        int targetPerPeriod,
        boolean active,
        StreakDto streak,
        LocalDate pausedUntil,
        HabitWeeklyProgress weeklyProgress
) {
}
