package com.habitloop.activity.service;

import com.habitloop.activity.dto.HabitPauseRange;
import com.habitloop.activity.dto.HabitWeeklyProgress;
import com.habitloop.activity.dto.HabitScheduleVersionDto;
import com.habitloop.activity.entity.Frequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HabitProgressCalculatorTest {

    @Test
    void dailyConsistencyUsesElapsedDaysInIstWeek() {
        LocalDate today = LocalDate.of(2026, 11, 26);

        HabitWeeklyProgress progress = HabitProgressCalculator.calculate(
                Frequency.DAILY, Map.of(), 1, today.minusMonths(1), today, 3, true, List.of());

        assertThat(progress.completed()).isEqualTo(3);
        assertThat(progress.expected()).isEqualTo(4);
        assertThat(progress.consistencyPercent()).isEqualTo(75);
    }

    @Test
    void customConsistencyCountsSelectedDaysAndIgnoresRestDays() {
        LocalDate today = LocalDate.of(2026, 11, 26);

        HabitWeeklyProgress progress = HabitProgressCalculator.calculate(
                Frequency.CUSTOM, Map.of("daysOfWeek", List.of(1, 3, 5)), 1,
                today.minusMonths(1), today, 2, true, List.of());

        assertThat(progress.expected()).isEqualTo(2);
        assertThat(progress.consistencyPercent()).isEqualTo(100);
    }

    @Test
    void pausedHabitDoesNotAccumulateExpectedCompletions() {
        LocalDate today = LocalDate.of(2026, 11, 26);

        HabitWeeklyProgress progress = HabitProgressCalculator.calculate(
                Frequency.DAILY, Map.of(), 1, today.minusMonths(1), today, 2, false, List.of());

        assertThat(progress.expected()).isZero();
        assertThat(progress.consistencyPercent()).isZero();
    }

    @Test
    void consistencyExcludesDaysCoveredByPreviousPausePeriods() {
        LocalDate today = LocalDate.of(2026, 11, 26);

        HabitWeeklyProgress progress = HabitProgressCalculator.calculate(
                Frequency.DAILY, Map.of(), 1, today.minusMonths(1), today, 2, true,
                List.of(new HabitPauseRange(today.minusDays(2), today)));

        assertThat(progress.expected()).isEqualTo(2);
        assertThat(progress.consistencyPercent()).isEqualTo(100);
    }

    @Test
    void consistencyUsesEachDayScheduleAfterMidweekEdit() {
        LocalDate monday = LocalDate.of(2026, 11, 23);
        var versions = List.of(
                new HabitScheduleVersionDto(monday.minusWeeks(1), Frequency.CUSTOM,
                        Map.of("daysOfWeek", List.of(1, 3, 5)), 1),
                new HabitScheduleVersionDto(monday.plusDays(1), Frequency.CUSTOM,
                        Map.of("daysOfWeek", List.of(2, 4)), 1));
        var progress = HabitProgressCalculator.calculate(
                versions, monday.minusWeeks(1), monday.plusDays(3), 2, true, List.of());
        assertThat(progress.expected()).isEqualTo(3);
    }

    @Test
    void timeOnlyEditsDoNotRestartTheWeeklyCap() {
        LocalDate monday = LocalDate.of(2026, 11, 23);
        var versions = List.of(
                new HabitScheduleVersionDto(monday.minusWeeks(1), Frequency.CUSTOM,
                        Map.of("daysOfWeek", List.of(1, 2, 3, 4), "perWeek", 2, "targetTime", "07:00"), 2),
                new HabitScheduleVersionDto(monday.plusDays(2), Frequency.CUSTOM,
                        Map.of("daysOfWeek", List.of(1, 2, 3, 4), "perWeek", 2, "targetTime", "08:00"), 2));
        var progress = HabitProgressCalculator.calculate(
                versions, monday.minusWeeks(1), monday.plusDays(3), 1, true, List.of());
        assertThat(progress.expected()).isEqualTo(2);
    }
}
