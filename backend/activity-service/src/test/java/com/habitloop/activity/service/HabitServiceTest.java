package com.habitloop.activity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.activity.dto.CompletionRequest;
import com.habitloop.activity.dto.CompletionResponse;
import com.habitloop.activity.dto.CreateHabitRequest;
import com.habitloop.activity.dto.HabitResponse;
import com.habitloop.activity.dto.PauseHabitRequest;
import com.habitloop.activity.dto.UpdateHabitRequest;
import com.habitloop.activity.entity.ActivityLog;
import com.habitloop.activity.entity.Frequency;
import com.habitloop.activity.entity.Habit;
import com.habitloop.activity.entity.HabitCompletion;
import com.habitloop.activity.entity.HabitStreak;
import com.habitloop.activity.entity.HabitScheduleVersion;
import com.habitloop.activity.event.ActivityLoggedEvent;
import com.habitloop.activity.mapper.ActivityMapper;
import com.habitloop.activity.repository.ActivityLogRepository;
import com.habitloop.activity.repository.HabitCompletionRepository;
import com.habitloop.activity.repository.HabitPausePeriodRepository;
import com.habitloop.activity.repository.HabitRepository;
import com.habitloop.activity.repository.HabitStreakRepository;
import com.habitloop.activity.repository.HabitScheduleVersionRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock HabitRepository habits;
    @Mock HabitCompletionRepository completions;
    @Mock HabitPausePeriodRepository pausePeriods;
    @Mock HabitStreakRepository streaks;
    @Mock HabitScheduleVersionRepository schedules;
    @Mock ActivityLogRepository activityLogs;
    @Mock ApplicationEventPublisher events;

    HabitService service;

    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID habitId = UUID.randomUUID();
    final List<HabitScheduleVersion> savedVersions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new HabitService(habits, completions, pausePeriods, streaks, schedules, activityLogs,
                new ActivityMapper(new ObjectMapper()), events);
        savedVersions.clear();
        savedVersions.add(new HabitScheduleVersion(habitId, tenantId, LocalDate.of(1, 1, 1),
                Frequency.DAILY, "{}", 1));
        lenient().when(schedules.save(any(HabitScheduleVersion.class))).thenAnswer(i -> {
            HabitScheduleVersion version = i.getArgument(0);
            savedVersions.add(version);
            return version;
        });
        lenient().when(schedules.findByHabitIdAndTenantIdOrderByEffectiveDate(any(), any()))
                .thenAnswer(i -> List.copyOf(savedVersions));
        lenient().doAnswer(i -> {
            LocalDate today = i.getArgument(2);
            savedVersions.removeIf(version -> version.getEffectiveDate().isAfter(today));
            return null;
        }).when(schedules).deleteByHabitIdAndTenantIdAndEffectiveDateAfter(any(), any(), any());
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
        savedVersions.clear();
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
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        when(habits.findForUpdateByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit));
        when(completions.existsByHabitIdAndCompletionDate(habitId, today))
                .thenReturn(false);
        when(completions.saveAndFlush(any(HabitCompletion.class))).thenAnswer(i -> {
            HabitCompletion c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(completions.findDates(habitId, tenantId)).thenReturn(List.of(today));
        when(streaks.findByHabitId(habitId)).thenReturn(Optional.empty());
        when(streaks.save(any(HabitStreak.class))).thenAnswer(i -> i.getArgument(0));
        when(activityLogs.save(any(ActivityLog.class))).thenAnswer(i -> {
            ActivityLog l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        CompletionResponse res = service.complete(habitId, new CompletionRequest(today, "ok"));

        assertThat(res.streak().current()).isEqualTo(1);
        assertThat(res.streak().longest()).isEqualTo(1);
        ArgumentCaptor<ActivityLog> logCaptor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogs).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getOccurredAt())
                .isEqualTo(today.atStartOfDay(TimeZones.INDIA).toOffsetDateTime());

        ArgumentCaptor<ActivityLoggedEvent> captor = ArgumentCaptor.forClass(ActivityLoggedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().activityType()).isEqualTo("HABIT_DONE");
        assertThat(captor.getValue().userId()).isEqualTo(userId.toString());
    }

    @Test
    void complete_duplicateSameDay_throwsConflict() {
        when(habits.findForUpdateByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit()));
        when(completions.existsByHabitIdAndCompletionDate(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.complete(habitId, new CompletionRequest(LocalDate.of(2026, 11, 23), null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void complete_futureDateIsRejected() {
        when(habits.findForUpdateByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
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

    @Test
    void historyUsesCompletionDateRatherThanCreationTime() {
        when(habits.findByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit()));
        var completion = new HabitCompletion();
        completion.setId(UUID.randomUUID());
        completion.setHabitId(habitId);
        completion.setCompletionDate(LocalDate.of(2026, 11, 20));
        completion.setSource(com.habitloop.activity.entity.ActivitySource.MANUAL);
        var pageable = PageRequest.of(0, 20);
        when(completions.findByHabitIdAndTenantId(habitId, tenantId, pageable))
                .thenReturn(new PageImpl<>(List.of(completion)));

        var result = service.completions(habitId, pageable);

        assertThat(result.content().get(0).occurredAt())
                .isEqualTo(LocalDate.of(2026, 11, 20).atStartOfDay(TimeZones.INDIA).toOffsetDateTime());
    }

    @Test
    void getRecalculatesExpiredCurrentStreakWithoutAnotherCompletion() {
        when(habits.findByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit()));
        when(completions.findDates(habitId, tenantId))
                .thenReturn(List.of(LocalDate.now(TimeZones.INDIA).minusDays(2)));

        HabitResponse response = service.get(habitId);

        assertThat(response.streak().current()).isZero();
        assertThat(response.streak().longest()).isEqualTo(1);
    }

    @Test
    void pauseAndResumeHabitUseTemporaryIstPauseDate() {
        Habit habit = habit();
        when(habits.findForUpdateByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit));

        HabitResponse paused = service.pause(habitId, new PauseHabitRequest(7));

        assertThat(paused.active()).isFalse();
        assertThat(paused.pausedUntil()).isEqualTo(LocalDate.now(TimeZones.INDIA).plusDays(7));

        HabitResponse resumed = service.resume(habitId);

        assertThat(resumed.active()).isTrue();
        assertThat(resumed.pausedUntil()).isNull();
    }

    @Test
    void weeklyChangeIsPendingUntilNextMondayAndCanBeCancelled() {
        when(habits.findForUpdateByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit()));
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        var weekly = new UpdateHabitRequest(null, null, Frequency.WEEKLY, Map.of("perWeek", 3), 3, null);

        HabitResponse updated = service.update(habitId, weekly);
        assertThat(updated.frequency()).isEqualTo(Frequency.DAILY);
        assertThat(updated.pendingSchedule().frequency()).isEqualTo(Frequency.WEEKLY);
        assertThat(updated.pendingSchedule().effectiveDate())
                .isEqualTo(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)));

        service.update(habitId, weekly);
        verify(schedules, times(1)).deleteByHabitIdAndTenantIdAndEffectiveDateAfter(habitId, tenantId, today);
        HabitResponse cancelled = service.update(habitId,
                new UpdateHabitRequest(null, null, Frequency.DAILY, Map.of(), 1, null));
        assertThat(cancelled.pendingSchedule()).isNull();
    }

    @Test
    void weekdayChangeTakesEffectNextDay() {
        when(habits.findForUpdateByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit()));
        HabitResponse updated = service.update(habitId,
                new UpdateHabitRequest(null, null, Frequency.CUSTOM, Map.of("daysOfWeek", List.of(1, 3)), 1, null));
        assertThat(updated.pendingSchedule().effectiveDate()).isEqualTo(LocalDate.now(TimeZones.INDIA).plusDays(1));
    }

    @Test
    void weeklyTimeChangeTakesEffectNextDayWithoutChangingTheWeeklyTarget() {
        savedVersions.clear();
        savedVersions.add(new HabitScheduleVersion(habitId, tenantId, LocalDate.of(1, 1, 1),
                Frequency.WEEKLY, "{\"perWeek\":3,\"targetTime\":\"07:00\"}", 3));
        when(habits.findForUpdateByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit()));
        HabitResponse updated = service.update(habitId,
                new UpdateHabitRequest(null, null, null, Map.of("perWeek", 3, "targetTime", "08:00"), null, null));
        assertThat(updated.pendingSchedule().effectiveDate()).isEqualTo(LocalDate.now(TimeZones.INDIA).plusDays(1));
        assertThat(updated.pendingSchedule().targetPerPeriod()).isEqualTo(3);
    }

    @Test
    void validSnapshotAvoidsLoadingOlderCompletions() {
        when(habits.findByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId, tenantId))
                .thenReturn(Optional.of(habit()));
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        HabitStreak snapshot = new HabitStreak();
        snapshot.setSnapshotDate(monday);
        snapshot.setCurrentStreak(2);
        snapshot.setSnapshotDayLongest(2);
        snapshot.setSnapshotWeekLongest(0);
        when(streaks.findByHabitId(habitId)).thenReturn(Optional.of(snapshot));
        when(completions.findDatesFrom(habitId, tenantId, monday)).thenReturn(
                java.util.stream.LongStream.rangeClosed(0, java.time.temporal.ChronoUnit.DAYS.between(monday, today))
                        .mapToObj(monday::plusDays).toList());

        HabitResponse response = service.get(habitId);

        assertThat(response.streak().current()).isEqualTo(2 + today.getDayOfWeek().getValue());
        verify(completions, never()).findDates(any(), any());
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
