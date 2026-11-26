package com.habitloop.activity.service;

import com.habitloop.activity.dto.HabitPauseRange;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Pure streak math, isolated for unit testing. */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    public record Result(int current, int longest, LocalDate lastCompleted) {
    }

    /**
     * Computes the next streak state after a completion on {@code date}.
     *
     * @param current       current streak before this completion
     * @param longest       longest streak so far
     * @param lastCompleted date of the previous completion (null if none)
     * @param date          the new completion date
     */
    public static Result next(int current, int longest, LocalDate lastCompleted, LocalDate date) {
        return next(current, longest, lastCompleted, date, Map.of(), List.of());
    }

    public static Result next(int current, int longest, LocalDate lastCompleted, LocalDate date,
                              Map<String, Object> schedule, List<HabitPauseRange> pauses) {
        if (!isScheduled(date, schedule) || isPaused(date, pauses)) {
            return new Result(current, longest, lastCompleted);
        }

        int newCurrent;
        LocalDate newLast;
        if (lastCompleted == null) {
            newCurrent = 1;
            newLast = date;
        } else if (date.equals(lastCompleted)) {
            newCurrent = Math.max(current, 1);
            newLast = lastCompleted;
        } else if (date.isAfter(lastCompleted)) {
            boolean missedScheduledDay = false;
            for (LocalDate day = lastCompleted.plusDays(1); day.isBefore(date); day = day.plusDays(1)) {
                if (isScheduled(day, schedule) && !isPaused(day, pauses)) {
                    missedScheduledDay = true;
                    break;
                }
            }
            newCurrent = missedScheduledDay ? 1 : current + 1;
            newLast = date;
        } else {
            // backfilled older date: keep current streak and the latest lastCompleted
            newCurrent = current;
            newLast = lastCompleted;
        }
        int newLongest = Math.max(longest, newCurrent);
        return new Result(newCurrent, newLongest, newLast);
    }

    private static boolean isScheduled(LocalDate date, Map<String, Object> schedule) {
        Object rawDays = schedule.get("daysOfWeek");
        if (!(rawDays instanceof List<?> days) || days.isEmpty()) {
            return true;
        }
        int dayOfWeek = date.getDayOfWeek().getValue();
        return days.stream().anyMatch(day -> day instanceof Number number && number.intValue() == dayOfWeek);
    }

    private static boolean isPaused(LocalDate date, List<HabitPauseRange> pauses) {
        return pauses.stream().anyMatch(pause ->
                !date.isBefore(pause.startDate()) && date.isBefore(pause.resumeDate()));
    }
}
