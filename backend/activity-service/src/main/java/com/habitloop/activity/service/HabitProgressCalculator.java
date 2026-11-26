package com.habitloop.activity.service;

import com.habitloop.activity.dto.HabitWeeklyProgress;
import com.habitloop.activity.dto.HabitPauseRange;
import com.habitloop.activity.entity.Frequency;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class HabitProgressCalculator {

    private HabitProgressCalculator() {
    }

    public static HabitWeeklyProgress calculate(Frequency frequency, Map<String, Object> schedule,
                                                int targetPerPeriod, LocalDate createdOn,
                                                LocalDate today, int completed, boolean active,
                                                List<HabitPauseRange> pauses) {
        if (!active) {
            return new HabitWeeklyProgress(completed, 0, 0);
        }

        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate start = createdOn.isAfter(weekStart) ? createdOn : weekStart;
        if (start.isAfter(today)) {
            return new HabitWeeklyProgress(completed, 0, 0);
        }

        int expected;
        Object rawDays = schedule.get("daysOfWeek");
        Object rawPerWeek = schedule.get("perWeek");
        if (rawDays instanceof List<?> days && !days.isEmpty()) {
            expected = 0;
            for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
                int weekday = date.getDayOfWeek().getValue();
                if (!isPaused(date, pauses) && days.stream().anyMatch(day -> day instanceof Number number
                        && number.intValue() == weekday)) {
                    expected++;
                }
            }
            if (rawPerWeek instanceof Number perWeek) {
                expected = Math.min(expected, perWeek.intValue());
            }
        } else if (frequency == Frequency.DAILY) {
            expected = countUnpausedDays(start, today, pauses);
        } else {
            int weeklyTarget = rawPerWeek instanceof Number perWeek
                    ? perWeek.intValue()
                    : targetPerPeriod;
            int elapsedFromStart = countUnpausedDays(start, today, pauses);
            expected = Math.min(weeklyTarget, (weeklyTarget * elapsedFromStart + 6) / 7);
        }

        int percentage = expected == 0 ? 0 : Math.min(100, completed * 100 / expected);
        return new HabitWeeklyProgress(completed, expected, percentage);
    }

    private static int countUnpausedDays(LocalDate from, LocalDate to, List<HabitPauseRange> pauses) {
        int count = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!isPaused(date, pauses)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isPaused(LocalDate date, List<HabitPauseRange> pauses) {
        return pauses.stream().anyMatch(pause ->
                !date.isBefore(pause.startDate()) && date.isBefore(pause.resumeDate()));
    }
}
