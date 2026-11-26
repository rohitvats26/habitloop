package com.habitloop.activity.dto;

import java.time.LocalDate;

public record HabitPauseRange(LocalDate startDate, LocalDate resumeDate) {
}
