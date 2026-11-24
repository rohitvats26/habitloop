package com.habitloop.activity.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
}
