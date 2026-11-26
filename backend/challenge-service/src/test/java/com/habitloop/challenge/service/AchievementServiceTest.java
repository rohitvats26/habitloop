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
        when(activityEvents.insertIfAbsent(any(), eq(tenantId), eq(userId), eq(activityLogId), eq(today)))
                .thenReturn(1);
        when(activityEvents.countByTenantIdAndUserId(tenantId, userId)).thenReturn(100L);
        when(activityEvents.findDistinctDates(tenantId, userId)).thenReturn(dates);

        service.recordActivity(tenantId, userId, activityLogId, "HABIT_DONE",
                OffsetDateTime.parse("2026-11-26T10:00:00+05:30"));

        verify(awards, times(6)).insertIfAbsent(any(UUID.class), eq(tenantId), eq(userId),
                anyString(), anyString(), anyString(), anyString(), any(OffsetDateTime.class));
    }

    @Test
    void duplicateActivityEventDoesNotRecalculateOrAward() {
        when(activityEvents.insertIfAbsent(any(), eq(tenantId), eq(userId), eq(activityLogId), any()))
                .thenReturn(0);

        service.recordActivity(tenantId, userId, activityLogId, "HABIT_DONE",
                OffsetDateTime.parse("2026-11-26T10:00:00+05:30"));

        verifyNoInteractions(awards);
        verify(activityEvents, never()).countByTenantIdAndUserId(any(), any());
    }

    @Test
    void nonHabitActivityDoesNotEarnHabitMilestones() {
        service.recordActivity(tenantId, userId, activityLogId, "PARTNER_ACTIVITY",
                OffsetDateTime.parse("2026-11-26T10:00:00+05:30"));

        verifyNoInteractions(activityEvents, awards);
    }
}
