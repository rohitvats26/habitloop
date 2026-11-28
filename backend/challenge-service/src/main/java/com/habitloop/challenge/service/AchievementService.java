package com.habitloop.challenge.service;

import com.habitloop.challenge.dto.AchievementResponse;
import com.habitloop.challenge.entity.AchievementAward;
import com.habitloop.challenge.mapper.AchievementMapper;
import com.habitloop.challenge.repository.AchievementActivityEventRepository;
import com.habitloop.challenge.repository.AchievementAwardRepository;
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.common.time.TimeZones;
import com.habitloop.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AchievementService {

    private static final List<Milestone> COMPLETION_MILESTONES = List.of(
            new Milestone(1, "FIRST_STEP", "First step", "You logged your first habit completion."),
            new Milestone(10, "TEN_COMPLETIONS", "Building momentum", "You completed 10 habits."),
            new Milestone(50, "FIFTY_COMPLETIONS", "Habit in motion", "You completed 50 habits."),
            new Milestone(100, "HUNDRED_COMPLETIONS", "A hundred small wins", "You completed 100 habits."));

    private static final List<Milestone> STREAK_MILESTONES = List.of(
            new Milestone(7, "SEVEN_DAY_STREAK", "One-week streak", "You kept a habit going for seven days."),
            new Milestone(30, "THIRTY_DAY_STREAK", "Thirty-day streak", "You kept a habit going for thirty days."));

    private final AchievementAwardRepository awards;
    private final AchievementActivityEventRepository activityEvents;
    private final AchievementMapper mapper;

    public AchievementService(AchievementAwardRepository awards,
                              AchievementActivityEventRepository activityEvents,
                              AchievementMapper mapper) {
        this.awards = awards;
        this.activityEvents = activityEvents;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AchievementResponse> mine(Pageable pageable) {
        return PageResponse.of(awards.findByTenantIdAndUserIdOrderByAwardedAtDesc(
                currentTenantId(), currentUserId(), pageable).map(mapper::toResponse));
    }

    @Transactional
    public void recordActivity(UUID tenantId, UUID userId, UUID activityLogId, UUID habitId,
                               String activityType, OffsetDateTime occurredAt) {
        if (!"HABIT_DONE".equals(activityType)) {
            return;
        }
        LocalDate date = occurredAt.atZoneSameInstant(TimeZones.INDIA).toLocalDate();
        activityEvents.ensureUser(tenantId, userId);
        activityEvents.lockUser(tenantId, userId);
        int inserted = habitId == null
                ? activityEvents.insertWithoutHabitIfAbsent(UUID.randomUUID(), tenantId, userId, activityLogId, date)
                : activityEvents.insertIfAbsent(UUID.randomUUID(), tenantId, userId, activityLogId, habitId, date);
        if (inserted == 0) {
            return;
        }

        long total = activityEvents.countByTenantIdAndUserId(tenantId, userId);
        for (Milestone milestone : COMPLETION_MILESTONES) {
            if (total >= milestone.threshold()) {
                award(tenantId, userId, milestone, "completion:" + milestone.threshold());
            }
        }

        int streak = habitId == null ? 0 : longestStreak(activityEvents.findDistinctDates(tenantId, userId, habitId));
        for (Milestone milestone : STREAK_MILESTONES) {
            if (streak >= milestone.threshold()) {
                award(tenantId, userId, milestone, "streak:" + milestone.threshold());
            }
        }
    }

    @Transactional
    public void awardChallengeParticipation(UUID tenantId, UUID userId, UUID challengeId) {
        award(tenantId, userId,
                new Milestone(1, "CHALLENGE_PARTICIPATION", "Challenge accepted",
                        "You joined a habit challenge."),
                "challenge:" + challengeId);
    }

    @Transactional
    public void awardChallengeCompletion(UUID tenantId, UUID userId, UUID challengeId) {
        award(tenantId, userId,
                new Milestone(1, "CHALLENGE_COMPLETED", "Challenge completed",
                        "You reached a challenge goal."),
                "challenge:" + challengeId);
    }

    private void award(UUID tenantId, UUID userId, Milestone milestone, String sourceKey) {
        awards.insertIfAbsent(UUID.randomUUID(), tenantId, userId, milestone.code(), sourceKey,
                milestone.title(), milestone.description(), OffsetDateTime.now(TimeZones.INDIA));
    }

    private int longestStreak(List<LocalDate> datesDescending) {
        if (datesDescending.isEmpty()) {
            return 0;
        }
        int streak = 1;
        int longest = 1;
        LocalDate previous = datesDescending.get(0);
        for (int i = 1; i < datesDescending.size(); i++) {
            LocalDate date = datesDescending.get(i);
            if (date.equals(previous.minusDays(1))) {
                streak++;
                longest = Math.max(longest, streak);
                previous = date;
            } else if (date.isBefore(previous.minusDays(1))) {
                streak = 1;
                previous = date;
            }
        }
        return longest;
    }

    private UUID currentTenantId() {
        return UUID.fromString(SecurityUtils.currentTenantId());
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityUtils.currentUserId());
    }

    private record Milestone(int threshold, String code, String title, String description) {
    }
}
