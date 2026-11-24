package com.habitloop.activity.service;

import java.time.LocalDate;

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
        int newCurrent;
        LocalDate newLast;
        if (lastCompleted == null) {
            newCurrent = 1;
            newLast = date;
        } else if (date.equals(lastCompleted)) {
            newCurrent = Math.max(current, 1);
            newLast = lastCompleted;
        } else if (date.equals(lastCompleted.plusDays(1))) {
            newCurrent = current + 1;
            newLast = date;
        } else if (date.isAfter(lastCompleted)) {
            newCurrent = 1; // gap broke the streak
            newLast = date;
        } else {
            // backfilled older date: keep current streak and the latest lastCompleted
            newCurrent = current;
            newLast = lastCompleted;
        }
        int newLongest = Math.max(longest, newCurrent);
        return new Result(newCurrent, newLongest, newLast);
    }
}
