package com.habitloop.activity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.activity.dto.CompletionRequest;
import com.habitloop.activity.dto.CompletionResponse;
import com.habitloop.activity.dto.CreateHabitRequest;
import com.habitloop.activity.dto.HabitResponse;
import com.habitloop.activity.entity.ActivityLog;
import com.habitloop.activity.entity.Frequency;
import com.habitloop.activity.entity.Habit;
import com.habitloop.activity.entity.HabitCompletion;
import com.habitloop.activity.entity.HabitStreak;
import com.habitloop.activity.event.ActivityLoggedEvent;
import com.habitloop.activity.mapper.ActivityMapper;
import com.habitloop.activity.repository.ActivityLogRepository;
import com.habitloop.activity.repository.HabitCompletionRepository;
import com.habitloop.activity.repository.HabitRepository;
import com.habitloop.activity.repository.HabitStreakRepository;
import com.habitloop.common.exception.DuplicateResourceException;
import com.habitloop.common.exception.InvalidRequestException;
import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.security.AuthUser;
import com.habitloop.common.time.TimeZones;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock HabitRepository habits;
    @Mock HabitCompletionRepository completions;
    @Mock HabitStreakRepository streaks;
    @Mock ActivityLogRepository activityLogs;
    @Mock ApplicationEventPublisher events;

    HabitService service;

    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID habitId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new HabitService(habits, completions, streaks, activityLogs,
                new ActivityMapper(new ObjectMapper()), events);
        var principal = new AuthUser(userId.toString(), tenantId.toString(), List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_persistsHabit_withSchedule() {
        when(habits.save(any(Habit.class))).thenAnswer(i -> {
            Habit h = i.getArgument(0);
            h.setId(habitId);
            return h;
        });
        CreateHabitRequest req = new CreateHabitRequest("Morning run", "5k", Frequency.CUSTOM,
                Map.of("perWeek", 3), 3);

        HabitResponse res = service.create(req);

        assertThat(res.id()).isEqualTo(habitId.toString());
        assertThat(res.name()).isEqualTo("Morning run");
        assertThat(res.frequency()).isEqualTo(Frequency.CUSTOM);
        assertThat(res.schedule()).containsEntry("perWeek", 3);
        assertThat(res.streak().current()).isZero();
    }

    @Test
    void create_invalidTargetIsRejectedInsteadOfSilentlyDefaulted() {
        CreateHabitRequest invalid = new CreateHabitRequest("Morning run", null, Frequency.DAILY, null, 0);

        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("targetPerPeriod must be greater than zero");
        verify(habits, never()).save(any());
    }

    @Test
    void complete_recordsCompletion_updatesStreak_andPublishesEvent() {
        Habit habit = habit();
        when(habits.findByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit));
        when(completions.existsByHabitIdAndCompletionDate(habitId, LocalDate.of(2026, 11, 23)))
                .thenReturn(false);
        when(completions.save(any(HabitCompletion.class))).thenAnswer(i -> {
            HabitCompletion c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(streaks.findByHabitId(habitId)).thenReturn(Optional.empty());
        when(streaks.save(any(HabitStreak.class))).thenAnswer(i -> i.getArgument(0));
        when(activityLogs.save(any(ActivityLog.class))).thenAnswer(i -> {
            ActivityLog l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        CompletionResponse res = service.complete(habitId, new CompletionRequest(LocalDate.of(2026, 11, 23), "ok"));

        assertThat(res.streak().current()).isEqualTo(1);
        assertThat(res.streak().longest()).isEqualTo(1);
        ArgumentCaptor<ActivityLog> logCaptor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogs).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getOccurredAt())
                .isEqualTo(LocalDate.of(2026, 11, 23).atStartOfDay(TimeZones.INDIA).toOffsetDateTime());

        ArgumentCaptor<ActivityLoggedEvent> captor = ArgumentCaptor.forClass(ActivityLoggedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().activityType()).isEqualTo("HABIT_DONE");
        assertThat(captor.getValue().userId()).isEqualTo(userId.toString());
    }

    @Test
    void complete_duplicateSameDay_throwsConflict() {
        when(habits.findByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit()));
        when(completions.existsByHabitIdAndCompletionDate(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.complete(habitId, new CompletionRequest(LocalDate.of(2026, 11, 23), null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void complete_futureDateIsRejected() {
        when(habits.findByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit()));
        LocalDate futureDate = LocalDate.now(TimeZones.INDIA).plusDays(1);

        assertThatThrownBy(() -> service.complete(habitId, new CompletionRequest(futureDate, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Completion date cannot be in the future");
        verify(completions, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void get_unknownOrCrossTenant_throwsNotFound() {
        UUID other = UUID.randomUUID();
        when(habits.findByIdAndUserIdAndTenantIdAndDeletedAtIsNull(other, userId, tenantId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(other)).isInstanceOf(ResourceNotFoundException.class);
    }

    private Habit habit() {
        Habit h = new Habit();
        h.setId(habitId);
        h.setTenantId(tenantId);
        h.setUserId(userId);
        h.setName("Morning run");
        h.setFrequency(Frequency.DAILY);
        h.setSchedule("{}");
        return h;
    }
}
