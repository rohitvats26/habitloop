package com.habitloop.activity.dto;

import com.habitloop.activity.entity.Frequency;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HabitScheduleRules {

    private static final Set<String> SUPPORTED_FIELDS = Set.of("targetTime", "daysOfWeek", "perWeek");

    private HabitScheduleRules() {
    }

    public static boolean isCompatible(Map<String, Object> schedule, Frequency frequency) {
        if (frequency == null || !isValid(schedule)) {
            return false;
        }
        boolean hasDays = schedule != null && schedule.containsKey("daysOfWeek");
        boolean hasWeeklyTarget = schedule != null && schedule.containsKey("perWeek");
        return switch (frequency) {
            case CUSTOM -> hasDays || hasWeeklyTarget;
            case WEEKLY -> !hasDays;
            case DAILY -> !hasDays && !hasWeeklyTarget;
        };
    }

    public static boolean isValid(Map<String, Object> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return true;
        }
        if (!SUPPORTED_FIELDS.containsAll(schedule.keySet())) {
            return false;
        }

        if (schedule.containsKey("targetTime")) {
            Object value = schedule.get("targetTime");
            if (!(value instanceof String time) || !time.matches("\\d{2}:\\d{2}")) {
                return false;
            }
            try {
                LocalTime.parse(time);
            } catch (DateTimeParseException ex) {
                return false;
            }
        }

        if (schedule.containsKey("daysOfWeek")) {
            Object value = schedule.get("daysOfWeek");
            if (!(value instanceof List<?> days) || days.isEmpty() || days.size() > 7) {
                return false;
            }
            Set<Integer> uniqueDays = new HashSet<>();
            for (Object day : days) {
                if (!(day instanceof Number number)) {
                    return false;
                }
                int dayNumber = number.intValue();
                if (number.doubleValue() != dayNumber || dayNumber < 1 || dayNumber > 7
                        || !uniqueDays.add(dayNumber)) {
                    return false;
                }
            }
        }

        if (schedule.containsKey("perWeek")) {
            Object value = schedule.get("perWeek");
            if (!(value instanceof Number number)) {
                return false;
            }
            int perWeek = number.intValue();
            if (number.doubleValue() != perWeek || perWeek < 1 || perWeek > 7) {
                return false;
            }
        }
        return true;
    }
}
