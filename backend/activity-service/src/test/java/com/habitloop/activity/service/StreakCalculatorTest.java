package com.habitloop.activity.service;

import com.habitloop.activity.dto.HabitPauseRange;
import com.habitloop.activity.dto.HabitScheduleVersionDto;
import com.habitloop.activity.entity.Frequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StreakCalculatorTest {

    private final LocalDate day = LocalDate.of(2026, 11, 23);

    @Test
    void firstCompletion_startsAtOne() {
        StreakCalculator.Result r = StreakCalculator.next(0, 0, null, day);
        assertThat(r.current()).isEqualTo(1);
        assertThat(r.longest()).isEqualTo(1);
        assertThat(r.lastCompleted()).isEqualTo(day);
    }

    @Test
    void consecutiveDay_incrementsStreak() {
        StreakCalculator.Result r = StreakCalculator.next(4, 6, day, day.plusDays(1));
        assertThat(r.current()).isEqualTo(5);
        assertThat(r.longest()).isEqualTo(6); // longest unchanged, still ahead
    }

    @Test
    void consecutiveDay_updatesLongestWhenExceeded() {
        StreakCalculator.Result r = StreakCalculator.next(6, 6, day, day.plusDays(1));
        assertThat(r.current()).isEqualTo(7);
        assertThat(r.longest()).isEqualTo(7);
    }

    @Test
    void gap_resetsCurrentButKeepsLongest() {
        StreakCalculator.Result r = StreakCalculator.next(10, 10, day, day.plusDays(3));
        assertThat(r.current()).isEqualTo(1);
        assertThat(r.longest()).isEqualTo(10);
    }

    @Test
    void sameDay_doesNotDoubleCount() {
        StreakCalculator.Result r = StreakCalculator.next(3, 5, day, day);
        assertThat(r.current()).isEqualTo(3);
        assertThat(r.lastCompleted()).isEqualTo(day);
    }

    @Test
    void customSchedule_doesNotBreakStreakOnRestDays() {
        StreakCalculator.Result r = StreakCalculator.next(
                4, 6, day, day.plusDays(2), Map.of("daysOfWeek", List.of(1, 3, 5)), List.of());
        assertThat(r.current()).isEqualTo(5);
        assertThat(r.lastCompleted()).isEqualTo(day.plusDays(2));
    }

    @Test
    void missedScheduledDay_resetsStreak() {
        StreakCalculator.Result r = StreakCalculator.next(
                4, 6, day, day.plusDays(4), Map.of("daysOfWeek", List.of(1, 3, 5)), List.of());
        assertThat(r.current()).isEqualTo(1);
        assertThat(r.lastCompleted()).isEqualTo(day.plusDays(4));
    }

    @Test
    void pauseDoesNotBreakStreak() {
        StreakCalculator.Result r = StreakCalculator.next(
                4, 6, day, day.plusDays(4), Map.of("daysOfWeek", List.of(1, 3, 5)),
                List.of(new HabitPauseRange(day.plusDays(1), day.plusDays(4))));
        assertThat(r.current()).isEqualTo(5);
        assertThat(r.lastCompleted()).isEqualTo(day.plusDays(4));
    }

    @Test
    void completionOnRestDayDoesNotAffectStreak() {
        StreakCalculator.Result r = StreakCalculator.next(
                4, 6, day, day.plusDays(1), Map.of("daysOfWeek", List.of(1, 3, 5)), List.of());
        assertThat(r.current()).isEqualTo(4);
        assertThat(r.lastCompleted()).isEqualTo(day);
    }

    @Test
    void backfilledCompletionRecomputesHistoryAndExpiredCurrentStreak() {
        var dates = List.of(day.plusDays(2), day, day.plusDays(1));
        var current = StreakCalculator.calculate(dates, day.plusDays(2), Frequency.DAILY, 1, Map.of(), List.of());
        assertThat(current.current()).isEqualTo(3);
        assertThat(current.longest()).isEqualTo(3);
        var expired = StreakCalculator.calculate(dates, day.plusDays(4), Frequency.DAILY, 1, Map.of(), List.of());
        assertThat(expired.current()).isZero();
        assertThat(expired.longest()).isEqualTo(3);
    }

    @Test
    void weeklyStreakCountsCompletedWeeksAndDoesNotPenalizeAnOngoingWeek() {
        var dates = List.of(day, day.plusDays(2), day.plusWeeks(1), day.plusWeeks(1).plusDays(2));
        var result = StreakCalculator.calculate(dates, day.plusWeeks(2), Frequency.WEEKLY, 2, Map.of(), List.of());
        assertThat(result.current()).isEqualTo(2);
        assertThat(result.longest()).isEqualTo(2);
        var missed = StreakCalculator.calculate(dates, day.plusWeeks(3), Frequency.WEEKLY, 2, Map.of(), List.of());
        assertThat(missed.current()).isZero();
        assertThat(missed.longest()).isEqualTo(2);
    }

    @Test
    void weekdayAndPauseDaysDoNotCauseAStaleStreakToExpire() {
        var result = StreakCalculator.calculate(List.of(day), day.plusDays(4), Frequency.CUSTOM, 1,
                Map.of("daysOfWeek", List.of(1, 3, 5)),
                List.of(new HabitPauseRange(day.plusDays(2), day.plusDays(3))));
        assertThat(result.current()).isEqualTo(1);
        assertThat(result.longest()).isEqualTo(1);
    }

    @Test
    void historicalDaysUseTheirOwnScheduleAndRespectPauses() {
        var versions = List.of(
                new HabitScheduleVersionDto(day.minusWeeks(1), Frequency.CUSTOM,
                        Map.of("daysOfWeek", List.of(1, 3)), 1),
                new HabitScheduleVersionDto(day.plusDays(1), Frequency.CUSTOM,
                        Map.of("daysOfWeek", List.of(2, 4)), 1));
        var result = StreakCalculator.calculateVersioned(List.of(day, day.plusDays(3)), day.plusDays(3),
                versions, List.of(new HabitPauseRange(day.plusDays(1), day.plusDays(2)))).result();
        assertThat(result.current()).isEqualTo(2);
        assertThat(result.longest()).isEqualTo(2);
    }

    @Test
    void changingUnitsStartsNewStreakAndKeepsBestInEachUnit() {
        var versions = List.of(
                new HabitScheduleVersionDto(day.minusWeeks(2), Frequency.DAILY, Map.of(), 1),
                new HabitScheduleVersionDto(day.plusWeeks(1), Frequency.WEEKLY, Map.of("perWeek", 2), 2));
        var dates = List.of(day, day.plusDays(1), day.plusWeeks(1), day.plusWeeks(1).plusDays(1));
        var result = StreakCalculator.calculateVersioned(dates, day.plusWeeks(1).plusDays(1),
                versions, List.of());
        assertThat(result.result().current()).isEqualTo(1);
        assertThat(result.result().longest()).isEqualTo(1);
        assertThat(result.dayLongest()).isEqualTo(2);
    }

    @Test
    void weeklyTargetChangeDoesNotRewritePreviousWeeks() {
        var versions = List.of(
                new HabitScheduleVersionDto(day.minusWeeks(1), Frequency.WEEKLY, Map.of("perWeek", 3), 3),
                new HabitScheduleVersionDto(day.plusWeeks(1), Frequency.WEEKLY, Map.of("perWeek", 2), 2),
                new HabitScheduleVersionDto(day.plusWeeks(1).plusDays(2), Frequency.WEEKLY,
                        Map.of("perWeek", 2, "targetTime", "08:00"), 2));
        var dates = List.of(day, day.plusDays(1), day.plusWeeks(1), day.plusWeeks(1).plusDays(1));
        var result = StreakCalculator.calculateVersioned(dates, day.plusWeeks(1).plusDays(3),
                versions, List.of()).result();
        assertThat(result.current()).isEqualTo(1);
        assertThat(result.longest()).isEqualTo(1);
    }

    @Test
    void snapshotAndFullHistoryAgreeAcrossScheduleChangesAndExpiredWeeks() {
        var versions = List.of(
                new HabitScheduleVersionDto(day.minusWeeks(2), Frequency.DAILY, Map.of(), 1),
                new HabitScheduleVersionDto(day.plusWeeks(1), Frequency.WEEKLY, Map.of("perWeek", 2), 2),
                new HabitScheduleVersionDto(day.plusWeeks(3), Frequency.CUSTOM,
                        Map.of("daysOfWeek", List.of(1, 3, 5)), 1));
        var dates = List.of(day, day.plusDays(1), day.plusWeeks(1), day.plusWeeks(1).plusDays(1),
                day.plusWeeks(2), day.plusWeeks(2).plusDays(1), day.plusWeeks(3));
        var pauses = List.of(new HabitPauseRange(day.plusWeeks(3).plusDays(2), day.plusWeeks(3).plusDays(3)));
        LocalDate boundary = day.plusWeeks(3);
        var baseline = StreakCalculator.calculateVersioned(
                dates.stream().filter(date -> date.isBefore(boundary)).toList(), boundary, versions, pauses);
        for (int offset = 0; offset < 21; offset++) {
            LocalDate today = boundary.plusDays(offset);
            var full = StreakCalculator.calculateVersioned(dates, today, versions, pauses);
            var incremental = StreakCalculator.advance(
                    dates.stream().filter(date -> !date.isBefore(boundary)).toList(),
                    boundary, today, versions, pauses, baseline);
            assertThat(incremental).as("snapshot on %s", today).isEqualTo(full);
        }
    }

    @Test
    void unchangedSchedulePreservesExistingStreakBehavior() {
        var variants = List.of(
                new HabitScheduleVersionDto(day.minusWeeks(1), Frequency.DAILY, Map.of(), 1),
                new HabitScheduleVersionDto(day.minusWeeks(1), Frequency.CUSTOM,
                        Map.of("daysOfWeek", List.of(1, 3, 5)), 1),
                new HabitScheduleVersionDto(day.minusWeeks(1), Frequency.WEEKLY, Map.of("perWeek", 2), 2));
        var pauses = List.of(new HabitPauseRange(day.plusDays(2), day.plusDays(4)));
        for (var version : variants) {
            for (int mask = 0; mask < 128; mask++) {
                var dates = new java.util.ArrayList<LocalDate>();
                for (int bit = 0; bit < 7; bit++) {
                    if ((mask & (1 << bit)) != 0) dates.add(day.plusDays(bit * 2L));
                }
                for (int offset = 0; offset < 21; offset++) {
                    LocalDate today = day.plusDays(offset);
                    var original = StreakCalculator.calculate(dates, today, version.frequency(),
                            version.targetPerPeriod(), version.schedule(), pauses);
                    var versioned = StreakCalculator.calculateVersioned(dates, today, List.of(version), pauses).result();
                    assertThat(versioned).as("%s mask %s date %s", version.frequency(), mask, today)
                            .isEqualTo(original);
                }
            }
        }
    }
}
