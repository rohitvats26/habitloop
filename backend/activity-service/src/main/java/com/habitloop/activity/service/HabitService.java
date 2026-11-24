package com.habitloop.activity.service;

import com.habitloop.activity.dto.CompletionRequest;
import com.habitloop.activity.dto.CompletionResponse;
import com.habitloop.activity.dto.CreateHabitRequest;
import com.habitloop.activity.dto.HabitResponse;
import com.habitloop.activity.dto.StreakDto;
import com.habitloop.activity.dto.UpdateHabitRequest;
import com.habitloop.activity.entity.ActivityLog;
import com.habitloop.activity.entity.ActivitySource;
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
import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.common.web.PageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class HabitService {

    private final HabitRepository habits;
    private final HabitCompletionRepository completions;
    private final HabitStreakRepository streaks;
    private final ActivityLogRepository activityLogs;
    private final ActivityMapper mapper;
    private final ApplicationEventPublisher events;

    public HabitService(HabitRepository habits, HabitCompletionRepository completions,
                        HabitStreakRepository streaks, ActivityLogRepository activityLogs,
                        ActivityMapper mapper, ApplicationEventPublisher events) {
        this.habits = habits;
        this.completions = completions;
        this.streaks = streaks;
        this.activityLogs = activityLogs;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional
    public HabitResponse create(CreateHabitRequest req) {
        Habit habit = new Habit();
        habit.setTenantId(tenantId());
        habit.setUserId(userId());
        habit.setName(req.name().trim());
        habit.setDescription(req.description());
        habit.setFrequency(req.frequency());
        habit.setSchedule(mapper.writeSchedule(req.schedule()));
        habit.setTargetPerPeriod(req.targetPerPeriod() <= 0 ? 1 : req.targetPerPeriod());
        habit = habits.save(habit);
        return mapper.toHabitResponse(habit, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<HabitResponse> list(Pageable pageable) {
        var page = habits.findByUserIdAndTenantIdAndDeletedAtIsNull(userId(), tenantId(), pageable)
                .map(h -> mapper.toHabitResponse(h, streaks.findByHabitId(h.getId()).orElse(null)));
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public HabitResponse get(UUID habitId) {
        Habit habit = requireHabit(habitId);
        return mapper.toHabitResponse(habit, streaks.findByHabitId(habitId).orElse(null));
    }

    @Transactional
    public HabitResponse update(UUID habitId, UpdateHabitRequest req) {
        Habit habit = requireHabit(habitId);
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
        if (req.targetPerPeriod() != null && req.targetPerPeriod() > 0) {
            habit.setTargetPerPeriod(req.targetPerPeriod());
        }
        if (req.active() != null) {
            habit.setActive(req.active());
        }
        habits.save(habit);
        return mapper.toHabitResponse(habit, streaks.findByHabitId(habitId).orElse(null));
    }

    @Transactional
    public void delete(UUID habitId) {
        Habit habit = requireHabit(habitId);
        habit.setDeletedAt(OffsetDateTime.now());
        habit.setActive(false);
        habits.save(habit);
    }

    @Transactional
    public CompletionResponse complete(UUID habitId, CompletionRequest req) {
        Habit habit = requireHabit(habitId);
        LocalDate date = req.date() != null ? req.date() : LocalDate.now();

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
        StreakCalculator.Result r = StreakCalculator.next(
                streak.getCurrentStreak(), streak.getLongestStreak(), streak.getLastCompleted(), date);
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
        log.setOccurredAt(date.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));
        log.setSource(ActivitySource.MANUAL);
        return activityLogs.save(log);
    }

    private Habit requireHabit(UUID habitId) {
        return habits.findByIdAndUserIdAndTenantIdAndDeletedAtIsNull(habitId, userId(), tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));
    }

    private UUID tenantId() {
        return UUID.fromString(SecurityUtils.currentTenantId());
    }

    private UUID userId() {
        return UUID.fromString(SecurityUtils.currentUserId());
    }
}
