package com.habitloop.activity.service;

import com.habitloop.activity.dto.CompletionRequest;
import com.habitloop.activity.dto.CompletionResponse;
import com.habitloop.activity.dto.CreateHabitRequest;
import com.habitloop.activity.dto.HabitResponse;
import com.habitloop.activity.dto.HabitPauseRange;
import com.habitloop.activity.dto.HabitScheduleRules;
import com.habitloop.activity.dto.HabitWeeklyProgress;
import com.habitloop.activity.dto.PauseHabitRequest;
import com.habitloop.activity.dto.StreakDto;
import com.habitloop.activity.dto.UpdateHabitRequest;
import com.habitloop.activity.entity.ActivityLog;
import com.habitloop.activity.entity.ActivitySource;
import com.habitloop.activity.entity.Habit;
import com.habitloop.activity.entity.HabitCompletion;
import com.habitloop.activity.entity.HabitPausePeriod;
import com.habitloop.activity.entity.HabitStreak;
import com.habitloop.activity.event.ActivityLoggedEvent;
import com.habitloop.activity.mapper.ActivityMapper;
import com.habitloop.activity.repository.ActivityLogRepository;
import com.habitloop.activity.repository.HabitCompletionRepository;
import com.habitloop.activity.repository.HabitPausePeriodRepository;
import com.habitloop.activity.repository.HabitRepository;
import com.habitloop.activity.repository.HabitStreakRepository;
import com.habitloop.common.exception.DuplicateResourceException;
import com.habitloop.common.exception.InvalidRequestException;
import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.common.time.TimeZones;
import com.habitloop.common.web.PageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

@Service
public class HabitService {

    private final HabitRepository habits;
    private final HabitCompletionRepository completions;
    private final HabitPausePeriodRepository pausePeriods;
    private final HabitStreakRepository streaks;
    private final ActivityLogRepository activityLogs;
    private final ActivityMapper mapper;
    private final ApplicationEventPublisher events;

    public HabitService(HabitRepository habits, HabitCompletionRepository completions,
                        HabitPausePeriodRepository pausePeriods,
                        HabitStreakRepository streaks, ActivityLogRepository activityLogs,
                        ActivityMapper mapper, ApplicationEventPublisher events) {
        this.habits = habits;
        this.completions = completions;
        this.pausePeriods = pausePeriods;
        this.streaks = streaks;
        this.activityLogs = activityLogs;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional
    public HabitResponse create(CreateHabitRequest req) {
        if (req.targetPerPeriod() <= 0) {
            throw new InvalidRequestException("targetPerPeriod must be greater than zero");
        }
        if (!HabitScheduleRules.isCompatible(req.schedule(), req.frequency())) {
            throw new InvalidRequestException("Schedule is invalid for the selected frequency");
        }
        Habit habit = new Habit();
        habit.setTenantId(tenantId());
        habit.setUserId(userId());
        habit.setName(req.name().trim());
        habit.setDescription(req.description());
        habit.setFrequency(req.frequency());
        habit.setSchedule(mapper.writeSchedule(req.schedule()));
        habit.setTargetPerPeriod(req.targetPerPeriod());
        habit = habits.save(habit);
        return toResponse(habit, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<HabitResponse> list(Pageable pageable) {
        var page = habits.findByUserIdAndTenantIdAndDeletedAtIsNull(userId(), tenantId(), pageable)
                .map(h -> toResponse(h, streaks.findByHabitId(h.getId()).orElse(null)));
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public HabitResponse get(UUID habitId) {
        Habit habit = requireHabit(habitId);
        return toResponse(habit, streaks.findByHabitId(habitId).orElse(null));
    }

    @Transactional
    public HabitResponse update(UUID habitId, UpdateHabitRequest req) {
        Habit habit = requireHabit(habitId);
        if (req.targetPerPeriod() != null && req.targetPerPeriod() <= 0) {
            throw new InvalidRequestException("targetPerPeriod must be greater than zero");
        }
        var frequency = req.frequency() == null ? habit.getFrequency() : req.frequency();
        var schedule = req.schedule() == null ? mapper.readSchedule(habit.getSchedule()) : req.schedule();
        if (!HabitScheduleRules.isCompatible(schedule, frequency)) {
            throw new InvalidRequestException("Schedule is invalid for the selected frequency");
        }
        if (StringUtils.hasText(req.name())) {
            habit.setName(req.name().trim());
        }
        if (req.description() != null) {
            habit.setDescription(req.description());
        }
        if (req.frequency() != null) {
            habit.setFrequency(req.frequency());
        }
        if (req.schedule() != null) {
            habit.setSchedule(mapper.writeSchedule(req.schedule()));
        }
        if (req.targetPerPeriod() != null) {
            habit.setTargetPerPeriod(req.targetPerPeriod());
        }
        if (req.active() != null) {
            habit.setActive(req.active());
            habit.setPausedUntil(null);
            closePausePeriod(habit, LocalDate.now(TimeZones.INDIA));
        }
        habits.save(habit);
        return toResponse(habit, streaks.findByHabitId(habitId).orElse(null));
    }

    @Transactional
    public HabitResponse pause(UUID habitId, PauseHabitRequest request) {
        Habit habit = requireHabit(habitId);
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        habit.setActive(false);
        LocalDate resumeDate = today.plusDays(request.days());
        closePausePeriod(habit, today);
        habit.setPausedUntil(resumeDate);
        HabitPausePeriod pause = new HabitPausePeriod();
        pause.setTenantId(habit.getTenantId());
        pause.setUserId(habit.getUserId());
        pause.setHabitId(habit.getId());
        pause.setStartDate(today);
        pause.setResumeDate(resumeDate);
        pausePeriods.save(pause);
        habits.save(habit);
        return toResponse(habit, streaks.findByHabitId(habitId).orElse(null));
    }

    @Transactional
    public HabitResponse resume(UUID habitId) {
        Habit habit = requireHabit(habitId);
        habit.setActive(true);
        closePausePeriod(habit, LocalDate.now(TimeZones.INDIA));
        habit.setPausedUntil(null);
        habits.save(habit);
        return toResponse(habit, streaks.findByHabitId(habitId).orElse(null));
    }

    @Transactional
    public void resumeExpiredPauses() {
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        for (Habit habit : habits.findByActiveFalseAndPausedUntilLessThanEqualAndDeletedAtIsNull(today)) {
            habit.setActive(true);
            habit.setPausedUntil(null);
            pausePeriods.findFirstByHabitIdAndTenantIdAndResumedAtIsNullOrderByStartDateDesc(
                            habit.getId(), habit.getTenantId())
                    .ifPresent(pause -> pause.setResumedAt(OffsetDateTime.now(TimeZones.INDIA)));
        }
    }

    @Transactional
    public void delete(UUID habitId) {
        Habit habit = requireHabit(habitId);
        habit.setDeletedAt(OffsetDateTime.now());
        habit.setActive(false);
        closePausePeriod(habit, LocalDate.now(TimeZones.INDIA));
        habit.setPausedUntil(null);
        habits.save(habit);
    }

    @Transactional
    public CompletionResponse complete(UUID habitId, CompletionRequest req) {
        Habit habit = requireHabit(habitId);
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        LocalDate date = req.date() != null ? req.date() : today;
        if (date.isAfter(today)) {
            throw new InvalidRequestException("Completion date cannot be in the future");
        }

        if (completions.existsByHabitIdAndCompletionDate(habitId, date)) {
            throw new DuplicateResourceException("Habit already completed on " + date);
        }

        HabitCompletion completion = new HabitCompletion();
        completion.setTenantId(habit.getTenantId());
        completion.setHabitId(habitId);
        completion.setUserId(habit.getUserId());
        completion.setCompletionDate(date);
        completion.setSource(ActivitySource.MANUAL);
        completion.setNote(req.note());
        completion = completions.save(completion);

        StreakDto streakDto = updateStreak(habit, date);
        ActivityLog log = writeActivityLog(habit, date);

        events.publishEvent(new ActivityLoggedEvent(
                habit.getTenantId().toString(), habit.getUserId().toString(), habitId.toString(),
                log.getId().toString(), "HABIT_DONE", log.getOccurredAt(), ActivitySource.MANUAL, null));

        return new CompletionResponse(completion.getId().toString(), date, streakDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<com.habitloop.activity.dto.ActivityLogResponse> completions(UUID habitId, Pageable pageable) {
        requireHabit(habitId);
        var page = completions.findByHabitIdAndTenantId(habitId, tenantId(), pageable)
                .map(c -> new com.habitloop.activity.dto.ActivityLogResponse(
                        c.getId().toString(), c.getHabitId().toString(), "HABIT_DONE",
                        null, null, c.getCreatedAt(), c.getSource()));
        return PageResponse.of(page);
    }

    private StreakDto updateStreak(Habit habit, LocalDate date) {
        HabitStreak streak = streaks.findByHabitId(habit.getId()).orElseGet(() -> {
            HabitStreak s = new HabitStreak();
            s.setHabitId(habit.getId());
            s.setTenantId(habit.getTenantId());
            s.setUserId(habit.getUserId());
            return s;
        });
        List<HabitPauseRange> pauses = streak.getLastCompleted() == null
                || !date.isAfter(streak.getLastCompleted()) ? List.of()
                : pausePeriods.findByHabitIdAndTenantIdAndStartDateLessThanAndResumeDateGreaterThan(
                                habit.getId(), habit.getTenantId(), date.plusDays(1),
                                streak.getLastCompleted().plusDays(1))
                        .stream()
                        .map(pause -> new HabitPauseRange(pause.getStartDate(), pause.getResumeDate()))
                        .toList();
        StreakCalculator.Result r = StreakCalculator.next(
                streak.getCurrentStreak(), streak.getLongestStreak(), streak.getLastCompleted(), date,
                mapper.readSchedule(habit.getSchedule()), pauses);
        streak.setCurrentStreak(r.current());
        streak.setLongestStreak(r.longest());
        streak.setLastCompleted(r.lastCompleted());
        streaks.save(streak);
        return new StreakDto(r.current(), r.longest());
    }

    private ActivityLog writeActivityLog(Habit habit, LocalDate date) {
        ActivityLog log = new ActivityLog();
        log.setTenantId(habit.getTenantId());
        log.setUserId(habit.getUserId());
        log.setHabitId(habit.getId());
        log.setActivityType("HABIT_DONE");
        log.setOccurredAt(date.atStartOfDay(TimeZones.INDIA).toOffsetDateTime());
        log.setSource(ActivitySource.MANUAL);
        return activityLogs.save(log);
    }

    private Habit requireHabit(UUID habitId) {
        return habits.findByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId(), tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));
    }

    private HabitResponse toResponse(Habit habit, HabitStreak streak) {
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        int completed = completions.countByHabitIdAndTenantIdAndCompletionDateBetween(
                habit.getId(), habit.getTenantId(), weekStart, today);
        LocalDate createdOn = habit.getCreatedAt() == null
                ? today
                : habit.getCreatedAt().atZoneSameInstant(TimeZones.INDIA).toLocalDate();
        var pauses = pausePeriods
                .findByHabitIdAndTenantIdAndStartDateLessThanAndResumeDateGreaterThan(
                        habit.getId(), habit.getTenantId(), today.plusDays(1), weekStart)
                .stream()
                .map(pause -> new HabitPauseRange(pause.getStartDate(), pause.getResumeDate()))
                .toList();
        HabitWeeklyProgress progress = HabitProgressCalculator.calculate(
                habit.getFrequency(), mapper.readSchedule(habit.getSchedule()), habit.getTargetPerPeriod(),
                createdOn, today, completed, habit.isActive(), pauses);
        return mapper.toHabitResponse(habit, streak, progress);
    }

    private void closePausePeriod(Habit habit, LocalDate resumeDate) {
        pausePeriods.findFirstByHabitIdAndTenantIdAndResumedAtIsNullOrderByStartDateDesc(
                        habit.getId(), habit.getTenantId())
                .ifPresent(pause -> {
                    if (resumeDate.isBefore(pause.getResumeDate())) {
                        pause.setResumeDate(resumeDate);
                    }
                    pause.setResumedAt(OffsetDateTime.now(TimeZones.INDIA));
                });
    }

    private UUID tenantId() {
        return UUID.fromString(SecurityUtils.currentTenantId());
    }

    private UUID userId() {
        return UUID.fromString(SecurityUtils.currentUserId());
    }
}
