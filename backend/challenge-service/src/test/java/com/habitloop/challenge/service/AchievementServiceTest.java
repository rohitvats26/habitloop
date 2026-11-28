package com.habitloop.challenge.service;

import com.habitloop.challenge.mapper.AchievementMapper;
import com.habitloop.challenge.repository.AchievementActivityEventRepository;
import com.habitloop.challenge.repository.AchievementAwardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock AchievementAwardRepository awards;
    @Mock AchievementActivityEventRepository activityEvents;

    AchievementService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID activityLogId = UUID.randomUUID();
    private final UUID habitId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AchievementService(awards, activityEvents, new AchievementMapper());
    }

    @Test
    void recordActivityAwardsCompletionAndStreakMilestonesOnce() {
        LocalDate today = LocalDate.of(2026, 11, 26);
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            dates.add(today.minusDays(i));
        }
        when(activityEvents.insertIfAbsent(any(), eq(tenantId), eq(userId), eq(activityLogId), eq(habitId), eq(today)))
                .thenReturn(1);
        when(activityEvents.countByTenantIdAndUserId(tenantId, userId)).thenReturn(100L);
        when(activityEvents.findDistinctDates(tenantId, userId, habitId)).thenReturn(dates);

        service.recordActivity(tenantId, userId, activityLogId, habitId, "HABIT_DONE",
                OffsetDateTime.parse("2026-11-26T10:00:00+05:30"));

        verify(awards, times(6)).insertIfAbsent(any(UUID.class), eq(tenantId), eq(userId),
                anyString(), anyString(), anyString(), anyString(), any(OffsetDateTime.class));
    }

    @Test
    void duplicateActivityEventDoesNotRecalculateOrAward() {
        when(activityEvents.insertIfAbsent(any(), eq(tenantId), eq(userId), eq(activityLogId), eq(habitId), any()))
                .thenReturn(0);

        service.recordActivity(tenantId, userId, activityLogId, habitId, "HABIT_DONE",
                OffsetDateTime.parse("2026-11-26T10:00:00+05:30"));

        verifyNoInteractions(awards);
        verify(activityEvents, never()).countByTenantIdAndUserId(any(), any());
    }

    @Test
    void nonHabitActivityDoesNotEarnHabitMilestones() {
        service.recordActivity(tenantId, userId, activityLogId, habitId, "PARTNER_ACTIVITY",
                OffsetDateTime.parse("2026-11-26T10:00:00+05:30"));

        verifyNoInteractions(activityEvents, awards);
    }

    @Test
    void backfilledHistoryAwardsStreakOnlyForTheSameHabit() {
        LocalDate today = LocalDate.of(2026, 11, 26);
        var dates = new ArrayList<LocalDate>();
        dates.add(today);
        for (int i = 3; i < 10; i++) {
            dates.add(today.minusDays(i));
        }
        when(activityEvents.insertIfAbsent(any(), eq(tenantId), eq(userId), eq(activityLogId), eq(habitId), eq(today)))
                .thenReturn(1);
        when(activityEvents.findDistinctDates(tenantId, userId, habitId)).thenReturn(dates);

        service.recordActivity(tenantId, userId, activityLogId, habitId, "HABIT_DONE",
                OffsetDateTime.parse("2026-11-26T10:00:00+05:30"));

        verify(awards).insertIfAbsent(any(), eq(tenantId), eq(userId), eq("SEVEN_DAY_STREAK"),
                eq("streak:7"), anyString(), anyString(), any());
        verify(activityEvents).findDistinctDates(tenantId, userId, habitId);
    }

    @Test
    void completionWithoutHabitCanEarnCountMilestonesButNotStreakBadges() {
        var date = LocalDate.of(2026, 11, 26);
        when(activityEvents.insertWithoutHabitIfAbsent(any(), eq(tenantId), eq(userId), eq(activityLogId), eq(date)))
                .thenReturn(1);
        when(activityEvents.countByTenantIdAndUserId(tenantId, userId)).thenReturn(1L);
        service.recordActivity(tenantId, userId, activityLogId, null, "HABIT_DONE",
                OffsetDateTime.parse("2026-11-26T10:00:00+05:30"));
        verify(awards).insertIfAbsent(any(), eq(tenantId), eq(userId), eq("FIRST_STEP"),
                eq("completion:1"), anyString(), anyString(), any());
        verify(activityEvents, never()).findDistinctDates(any(), any(), any());
    }
}
