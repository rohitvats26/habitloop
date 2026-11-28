package com.habitloop.activity.service;

import com.habitloop.activity.dto.HabitPauseRange;
import com.habitloop.activity.dto.HabitScheduleVersionDto;
import com.habitloop.activity.entity.Frequency;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Pure streak math, isolated for unit testing. */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    public record Result(int current, int longest, LocalDate lastCompleted) {
    }

    public record Snapshot(int current, int dayLongest, int weekLongest, LocalDate lastCompleted,
                           boolean weekly) {
        public Result result() {
            return new Result(current, weekly ? weekLongest : dayLongest, lastCompleted);
        }
    }

    public static boolean isWeekly(HabitScheduleVersionDto version) {
        return version.frequency() != Frequency.DAILY
                && !(version.schedule().get("daysOfWeek") instanceof List<?> days && !days.isEmpty());
    }

    public static HabitScheduleVersionDto scheduleAt(List<HabitScheduleVersionDto> versions, LocalDate date) {
        HabitScheduleVersionDto selected = null;
        for (HabitScheduleVersionDto version : versions) {
            if (version.effectiveDate().isAfter(date)) {
                break;
            }
            selected = version;
        }
        if (selected == null) {
            throw new IllegalStateException("Habit schedule history has no baseline");
        }
        return selected;
    }

    public static Snapshot calculateVersioned(List<LocalDate> dates, LocalDate today,
                                              List<HabitScheduleVersionDto> versions,
                                              List<HabitPauseRange> pauses) {
        var sorted = dates.stream().filter(date -> !date.isAfter(today)).distinct().sorted().toList();
        if (sorted.isEmpty()) {
            return new Snapshot(0, 0, 0, null, isWeekly(scheduleAt(versions, today)));
        }
        LocalDate start = sorted.get(0).with(DayOfWeek.MONDAY);
        var initial = new Snapshot(0, 0, 0, null, isWeekly(scheduleAt(versions, start)));
        return advance(sorted, start, today, versions, pauses, initial);
    }

    public static Snapshot advance(List<LocalDate> dates, LocalDate start, LocalDate today,
                                   List<HabitScheduleVersionDto> versions, List<HabitPauseRange> pauses,
                                   Snapshot initial) {
        var completed = new HashSet<>(dates);
        int current = initial.current();
        int dayLongest = initial.dayLongest();
        int weekLongest = initial.weekLongest();
        LocalDate last = initial.lastCompleted();
        boolean weekly = initial.weekly();
        LocalDate day = start;
        while (!day.isAfter(today)) {
            HabitScheduleVersionDto version = scheduleAt(versions, day);
            boolean nextWeekly = isWeekly(version);
            if (weekly != nextWeekly) {
                current = 0;
                last = null;
                weekly = nextWeekly;
            }
            if (weekly) {
                if (day.getDayOfWeek() != DayOfWeek.MONDAY) {
                    throw new IllegalStateException("Weekly streak schedule must start on Monday");
                }
                int count = 0;
                int unpaused = 0;
                for (int i = 0; i < 7; i++) {
                    LocalDate date = day.plusDays(i);
                    if (!isPaused(date, pauses)) {
                        unpaused++;
                        if (!date.isAfter(today) && completed.contains(date)) {
                            count++;
                            last = date;
                        }
                    }
                }
                int target = version.schedule().get("perWeek") instanceof Number perWeek
                        ? perWeek.intValue() : version.targetPerPeriod();
                if (unpaused > 0 && count >= target) {
                    current++;
                    weekLongest = Math.max(weekLongest, current);
                } else if (unpaused > 0 && day.plusDays(6).isBefore(today)) {
                    current = 0;
                }
                day = day.plusWeeks(1);
            } else {
                if (isScheduled(day, version.schedule()) && !isPaused(day, pauses)) {
                    if (completed.contains(day)) {
                        current++;
                        last = day;
                        dayLongest = Math.max(dayLongest, current);
                    } else if (day.isBefore(today)) {
                        current = 0;
                    }
                }
                day = day.plusDays(1);
            }
        }
        return new Snapshot(current, dayLongest, weekLongest, last, weekly);
    }

    public static Result calculate(List<LocalDate> dates, LocalDate today, Frequency frequency,
                                   int target, Map<String, Object> schedule, List<HabitPauseRange> pauses) {
        var sorted = dates.stream().filter(date -> !date.isAfter(today)).distinct().sorted().toList();
        if (sorted.isEmpty()) {
            return new Result(0, 0, null);
        }
        if (frequency != Frequency.DAILY && !(schedule.get("daysOfWeek") instanceof List<?> days && !days.isEmpty())) {
            int weeklyTarget = schedule.get("perWeek") instanceof Number perWeek ? perWeek.intValue() : target;
            return weekly(sorted, today, weeklyTarget, pauses);
        }
        Result result = new Result(0, 0, null);
        for (LocalDate date : sorted) {
            result = next(result.current(), result.longest(), result.lastCompleted(), date, schedule, pauses);
        }
        if (result.lastCompleted() != null) {
            for (LocalDate date = result.lastCompleted().plusDays(1); date.isBefore(today); date = date.plusDays(1)) {
                if (isScheduled(date, schedule) && !isPaused(date, pauses)) {
                    return new Result(0, result.longest(), result.lastCompleted());
                }
            }
        }
        return result;
    }

    private static Result weekly(List<LocalDate> dates, LocalDate today, int target, List<HabitPauseRange> pauses) {
        var completions = new HashSet<>(dates);
        LocalDate currentWeek = today.with(DayOfWeek.MONDAY);
        LocalDate last = null;
        int current = 0;
        int longest = 0;
        for (LocalDate week = dates.get(0).with(DayOfWeek.MONDAY); !week.isAfter(currentWeek); week = week.plusWeeks(1)) {
            int completed = 0;
            int unpaused = 0;
            for (int i = 0; i < 7; i++) {
                LocalDate date = week.plusDays(i);
                if (!isPaused(date, pauses)) {
                    unpaused++;
                    if (completions.contains(date)) {
                        completed++;
                        last = date;
                    }
                }
            }
            if (unpaused == 0) {
                continue;
            }
            if (completed >= target) {
                current++;
                longest = Math.max(longest, current);
            } else if (week.isBefore(currentWeek)) {
                current = 0;
            }
        }
        return new Result(current, longest, last);
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
