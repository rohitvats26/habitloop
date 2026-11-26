package com.habitloop.challenge.service;

import com.habitloop.challenge.entity.Challenge;
import com.habitloop.challenge.entity.ChallengeParticipant;
import com.habitloop.challenge.entity.ChallengeStatus;
import com.habitloop.challenge.entity.Visibility;
import com.habitloop.challenge.repository.ChallengeParticipantRepository;
import com.habitloop.challenge.repository.ChallengeProgressEventRepository;
import com.habitloop.challenge.repository.ChallengeRepository;
import com.habitloop.common.time.TimeZones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock ChallengeParticipantRepository participants;
    @Mock ChallengeRepository challenges;
    @Mock ChallengeProgressEventRepository progressEvents;
    @Mock AchievementService achievements;

    ProgressService service;

    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID challengeId = UUID.randomUUID();
    final UUID participantId = UUID.randomUUID();
    final UUID activityLogId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProgressService(participants, challenges, progressEvents, achievements);
    }

    @Test
    void applyActivity_incrementsProgress_forActiveChallenge() {
        ChallengeParticipant p = participant(BigDecimal.ZERO);
        when(participants.findByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of(p));
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId))
                .thenReturn(Optional.of(challenge(new BigDecimal("5"))));
        when(progressEvents.existsByParticipantIdAndActivityLogId(participantId, activityLogId)).thenReturn(false);

        service.applyActivity(tenantId, userId, activityLogId, now());

        assertThat(p.getProgress()).isEqualByComparingTo("1");
        assertThat(p.isCompleted()).isFalse();
        verify(participants).save(p);
    }

    @Test
    void applyActivity_duplicate_isSkipped() {
        ChallengeParticipant p = participant(BigDecimal.ZERO);
        when(participants.findByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of(p));
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId))
                .thenReturn(Optional.of(challenge(new BigDecimal("5"))));
        when(progressEvents.existsByParticipantIdAndActivityLogId(participantId, activityLogId)).thenReturn(true);

        service.applyActivity(tenantId, userId, activityLogId, now());

        assertThat(p.getProgress()).isEqualByComparingTo("0");
        verify(participants, never()).save(any());
    }

    @Test
    void applyActivity_reachingTarget_marksCompleted() {
        ChallengeParticipant p = participant(new BigDecimal("4"));
        when(participants.findByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of(p));
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId))
                .thenReturn(Optional.of(challenge(new BigDecimal("5"))));
        when(progressEvents.existsByParticipantIdAndActivityLogId(participantId, activityLogId)).thenReturn(false);

        service.applyActivity(tenantId, userId, activityLogId, now());

        assertThat(p.getProgress()).isEqualByComparingTo("5");
        assertThat(p.isCompleted()).isTrue();
        verify(achievements).awardChallengeCompletion(tenantId, userId, challengeId);
    }

    @Test
    void delayedActivityOnEndDateCountsEvenWhenProcessedLater() {
        ChallengeParticipant p = participant(BigDecimal.ZERO);
        Challenge c = challenge(new BigDecimal("5"));
        LocalDate end = LocalDate.now(TimeZones.INDIA).minusDays(1);
        c.setEndsOn(end);
        c.setStartsOn(end.minusDays(2));
        when(participants.findByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of(p));
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId)).thenReturn(Optional.of(c));

        service.applyActivity(tenantId, userId, activityLogId, end.atTime(23, 30)
                .atZone(TimeZones.INDIA).toOffsetDateTime().withOffsetSameInstant(java.time.ZoneOffset.UTC));

        assertThat(p.getProgress()).isEqualByComparingTo("1");
    }

    @Test
    void activityOutsideWindowOrBeforeJoiningDoesNotCount() {
        ChallengeParticipant p = participant(BigDecimal.ZERO);
        Challenge c = challenge(new BigDecimal("5"));
        when(participants.findByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of(p));
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId)).thenReturn(Optional.of(c));
        var outsideWindow = c.getEndsOn().plusDays(1).atStartOfDay(TimeZones.INDIA).toOffsetDateTime();
        var beforeJoining = LocalDate.now(TimeZones.INDIA).minusDays(6)
                .atStartOfDay(TimeZones.INDIA).toOffsetDateTime();
        c.setStartsOn(LocalDate.now(TimeZones.INDIA).minusDays(10));

        service.applyActivity(tenantId, userId, activityLogId, outsideWindow);
        service.applyActivity(tenantId, userId, activityLogId, beforeJoining);

        verify(participants, never()).save(any());
    }

    private ChallengeParticipant participant(BigDecimal progress) {
        ChallengeParticipant p = new ChallengeParticipant();
        p.setId(participantId);
        p.setTenantId(tenantId);
        p.setChallengeId(challengeId);
        p.setUserId(userId);
        p.setProgress(progress);
        ReflectionTestUtils.setField(p, "joinedAt", LocalDate.now(TimeZones.INDIA).minusDays(5)
                .atStartOfDay(TimeZones.INDIA).toOffsetDateTime());
        return p;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(TimeZones.INDIA);
    }

    private Challenge challenge(BigDecimal target) {
        Challenge c = new Challenge();
        c.setId(challengeId);
        c.setTenantId(tenantId);
        c.setCreatorId(userId);
        c.setVisibility(Visibility.PUBLIC);
        c.setMetric("DAYS_COMPLETED");
        c.setTarget(target);
        c.setStatus(ChallengeStatus.ACTIVE);
        c.setStartsOn(LocalDate.now(TimeZones.INDIA).minusDays(1));
        c.setEndsOn(LocalDate.now(TimeZones.INDIA).plusDays(10));
        return c;
    }
}
