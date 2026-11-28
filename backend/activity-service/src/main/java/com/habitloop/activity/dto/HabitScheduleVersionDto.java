package com.habitloop.activity.dto;

import com.habitloop.activity.entity.Frequency;

import java.time.LocalDate;
import java.util.Map;

public record HabitScheduleVersionDto(LocalDate effectiveDate, Frequency frequency,
                                      Map<String, Object> schedule, int targetPerPeriod) {
}
