package com.habitloop.activity.dto;

public record HabitWeeklyProgress(
        int completed,
        int expected,
        int consistencyPercent
) {
}
