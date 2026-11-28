package com.habitloop.activity.service;

import com.habitloop.activity.dto.CompletionRequest;
import com.habitloop.activity.dto.CompletionResponse;
import com.habitloop.activity.dto.CreateHabitRequest;
import com.habitloop.activity.dto.HabitResponse;
import com.habitloop.activity.dto.HabitPauseRange;
import com.habitloop.activity.dto.HabitScheduleRules;
import com.habitloop.activity.dto.HabitScheduleVersionDto;
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
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.common.time.TimeZones;
import com.habitloop.common.web.PageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class HabitService {

    private final HabitRepository habits;
    private final HabitCompletionRepository completions;
    private final HabitPausePeriodRepository pausePeriods;
    private final HabitStreakRepository streaks;
    private final HabitScheduleVersionRepository schedules;
    private final ActivityLogRepository activityLogs;
    private final ActivityMapper mapper;
    private final ApplicationEventPublisher events;

    public HabitService(HabitRepository habits, HabitCompletionRepository completions,
                        HabitPausePeriodRepository pausePeriods,
                        HabitStreakRepository streaks, HabitScheduleVersionRepository schedules,
                        ActivityLogRepository activityLogs,
                        ActivityMapper mapper, ApplicationEventPublisher events) {
        this.habits = habits;
        this.completions = completions;
        this.pausePeriods = pausePeriods;
        this.streaks = streaks;
        this.schedules = schedules;
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
        schedules.save(new HabitScheduleVersion(habit.getId(), habit.getTenantId(), LocalDate.of(1, 1, 1),
                habit.getFrequency(), habit.getSchedule(), habit.getTargetPerPeriod()));
        refreshStreakSnapshot(habit, LocalDate.now(TimeZones.INDIA));
        return toResponse(habit);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<HabitResponse> list(Pageable pageable) {
        var page = habits.findByUserIdAndTenantIdAndDeletedAtIsNull(userId(), tenantId(), pageable)
                .map(this::toResponse);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HabitResponse get(UUID habitId) {
        Habit habit = requireHabit(habitId);
        return toResponse(habit);
    }

    @Transactional
    public HabitResponse update(UUID habitId, UpdateHabitRequest req) {
        Habit habit = requireHabitForUpdate(habitId);
        if (req.targetPerPeriod() != null && req.targetPerPeriod() <= 0) {
            throw new InvalidRequestException("targetPerPeriod must be greater than zero");
        }
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        var versions = scheduleVersions(habit);
        var active = StreakCalculator.scheduleAt(versions, today);
        var pending = versions.stream().filter(version -> version.effectiveDate().isAfter(today)).findFirst().orElse(null);
        var base = pending == null ? active : pending;
        var frequency = req.frequency() == null ? base.frequency() : req.frequency();
        var schedule = req.schedule() == null ? base.schedule() : req.schedule();
        int target = req.targetPerPeriod() == null ? base.targetPerPeriod() : req.targetPerPeriod();
        if (!HabitScheduleRules.isCompatible(schedule, frequency)) {
            throw new InvalidRequestException("Schedule is invalid for the selected frequency");
        }
        if (StringUtils.hasText(req.name())) {
            habit.setName(req.name().trim());
        }
        if (req.description() != null) {
            habit.setDescription(req.description());
        }
        var desired = new HabitScheduleVersionDto(today, frequency, schedule, target);
        if (!sameSchedule(base, desired)) {
            schedules.deleteByHabitIdAndTenantIdAndEffectiveDateAfter(habit.getId(), habit.getTenantId(), today);
            schedules.flush();
            if (!sameSchedule(active, desired)) {
                boolean affectsWeeklyStreak = (StreakCalculator.isWeekly(active) || StreakCalculator.isWeekly(desired))
                        && !sameStreakRules(active, desired);
                LocalDate effective = affectsWeeklyStreak
                        ? today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) : today.plusDays(1);
                schedules.save(new HabitScheduleVersion(habit.getId(), habit.getTenantId(), effective,
                        frequency, mapper.writeSchedule(schedule), target));
            }
        }
        if (req.active() != null) {
            habit.setActive(req.active());
            habit.setPausedUntil(null);
            closePausePeriod(habit, LocalDate.now(TimeZones.INDIA));
        }
        habits.save(habit);
        refreshStreakSnapshot(habit, today);
        return toResponse(habit);
    }

    @Transactional
    public HabitResponse pause(UUID habitId, PauseHabitRequest request) {
        Habit habit = requireHabitForUpdate(habitId);
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
        refreshStreakSnapshot(habit, today);
        return toResponse(habit);
    }

    @Transactional
    public HabitResponse resume(UUID habitId) {
        Habit habit = requireHabitForUpdate(habitId);
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        habit.setActive(true);
        closePausePeriod(habit, today);
        habit.setPausedUntil(null);
        habits.save(habit);
        refreshStreakSnapshot(habit, today);
        return toResponse(habit);
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
        Habit habit = requireHabitForUpdate(habitId);
        habit.setDeletedAt(OffsetDateTime.now());
        habit.setActive(false);
        closePausePeriod(habit, LocalDate.now(TimeZones.INDIA));
        habit.setPausedUntil(null);
        habits.save(habit);
    }

    @Transactional
    public CompletionResponse complete(UUID habitId, CompletionRequest req) {
        Habit habit = habits.findForUpdateByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId(), tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));
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
        completion = completions.saveAndFlush(completion);

        StreakDto streakDto = updateStreak(habit, today);
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
                        null, null, c.getCompletionDate().atStartOfDay(TimeZones.INDIA).toOffsetDateTime(),
                        c.getSource()));
        return PageResponse.of(page);
    }

    private StreakDto updateStreak(Habit habit, LocalDate today) {
        refreshStreakSnapshot(habit, today);
        StreakCalculator.Result r = calculateStreak(habit, today, scheduleVersions(habit));
        return new StreakDto(r.current(), r.longest());
    }

    private void refreshStreakSnapshot(Habit habit, LocalDate today) {
        HabitStreak streak = streaks.findByHabitId(habit.getId()).orElseGet(() -> {
            HabitStreak s = new HabitStreak();
            s.setHabitId(habit.getId());
            s.setTenantId(habit.getTenantId());
            s.setUserId(habit.getUserId());
            return s;
        });
        List<LocalDate> dates = completions.findDates(habit.getId(), habit.getTenantId());
        LocalDate boundary = today.with(DayOfWeek.MONDAY);
        List<LocalDate> prior = dates.stream().filter(date -> date.isBefore(boundary)).toList();
        LocalDate first = prior.isEmpty() ? boundary : prior.get(0);
        var snapshot = StreakCalculator.calculateVersioned(prior, boundary, scheduleVersions(habit),
                pauseRanges(habit, first, boundary));
        streak.setCurrentStreak(snapshot.current());
        streak.setLongestStreak(snapshot.result().longest());
        streak.setLastCompleted(snapshot.lastCompleted());
        streak.setSnapshotDate(boundary);
        streak.setSnapshotDayLongest(snapshot.dayLongest());
        streak.setSnapshotWeekLongest(snapshot.weekLongest());
        streaks.save(streak);
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

    private Habit requireHabitForUpdate(UUID habitId) {
        return habits.findForUpdateByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId(), tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));
    }

    private HabitResponse toResponse(Habit habit) {
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        var versions = scheduleVersions(habit);
        var active = StreakCalculator.scheduleAt(versions, today);
        var pending = versions.stream().filter(version -> version.effectiveDate().isAfter(today)).findFirst().orElse(null);
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
                versions, createdOn, today, completed, habit.isActive(), pauses);
        StreakCalculator.Result calculated = calculateStreak(habit, today, versions);
        HabitStreak displayStreak = new HabitStreak();
        displayStreak.setCurrentStreak(calculated.current());
        displayStreak.setLongestStreak(calculated.longest());
        return mapper.toHabitResponse(habit, displayStreak, progress, active, pending);
    }

    private StreakCalculator.Result calculateStreak(Habit habit, LocalDate today,
                                                    List<HabitScheduleVersionDto> versions) {
        var stored = streaks.findByHabitId(habit.getId()).orElse(null);
        if (stored != null && stored.getSnapshotDate() != null && !stored.getSnapshotDate().isAfter(today)
                && stored.getSnapshotDayLongest() != null && stored.getSnapshotWeekLongest() != null) {
            LocalDate from = stored.getSnapshotDate();
            List<LocalDate> dates = completions.findDatesFrom(habit.getId(), habit.getTenantId(), from);
            var seed = new StreakCalculator.Snapshot(stored.getCurrentStreak(), stored.getSnapshotDayLongest(),
                    stored.getSnapshotWeekLongest(), stored.getLastCompleted(),
                    StreakCalculator.isWeekly(StreakCalculator.scheduleAt(versions, from)));
            return StreakCalculator.advance(dates, from, today, versions, pauseRanges(habit, from, today), seed).result();
        }
        List<LocalDate> dates = completions.findDates(habit.getId(), habit.getTenantId());
        LocalDate first = dates.isEmpty() ? today : dates.get(0);
        return StreakCalculator.calculateVersioned(dates, today, versions, pauseRanges(habit, first, today)).result();
    }

    private List<HabitPauseRange> pauseRanges(Habit habit, LocalDate from, LocalDate to) {
        return pausePeriods
                .findByHabitIdAndTenantIdAndStartDateLessThanAndResumeDateGreaterThan(
                        habit.getId(), habit.getTenantId(), to.plusDays(1), from)
                .stream().map(pause -> new HabitPauseRange(pause.getStartDate(), pause.getResumeDate())).toList();
    }

    private List<HabitScheduleVersionDto> scheduleVersions(Habit habit) {
        var versions = schedules.findByHabitIdAndTenantIdOrderByEffectiveDate(habit.getId(), habit.getTenantId())
                .stream().map(version -> new HabitScheduleVersionDto(version.getEffectiveDate(),
                        version.getFrequency(), mapper.readSchedule(version.getSchedule()), version.getTargetPerPeriod()))
                .toList();
        if (versions.isEmpty()) {
            throw new IllegalStateException("Habit schedule history has no baseline");
        }
        return versions;
    }

    private static boolean sameSchedule(HabitScheduleVersionDto left, HabitScheduleVersionDto right) {
        return left.frequency() == right.frequency() && left.targetPerPeriod() == right.targetPerPeriod()
                && left.schedule().equals(right.schedule());
    }

    private static boolean sameStreakRules(HabitScheduleVersionDto left, HabitScheduleVersionDto right) {
        boolean weekly = StreakCalculator.isWeekly(left);
        if (weekly != StreakCalculator.isWeekly(right)) {
            return false;
        }
        if (weekly) {
            Object leftTarget = left.schedule().getOrDefault("perWeek", left.targetPerPeriod());
            Object rightTarget = right.schedule().getOrDefault("perWeek", right.targetPerPeriod());
            return Objects.equals(leftTarget, rightTarget);
        }
        return Objects.equals(left.schedule().get("daysOfWeek"), right.schedule().get("daysOfWeek"));
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
