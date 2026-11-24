package com.habitloop.activity.dto;

public record StreakDto(int current, int longest) {

    public static StreakDto zero() {
        return new StreakDto(0, 0);
    }
}
