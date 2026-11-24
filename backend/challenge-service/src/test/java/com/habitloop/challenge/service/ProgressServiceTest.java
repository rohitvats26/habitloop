package com.habitloop.challenge.service;

import com.habitloop.challenge.entity.Challenge;
import com.habitloop.challenge.entity.ChallengeParticipant;
import com.habitloop.challenge.entity.ChallengeStatus;
import com.habitloop.challenge.entity.Visibility;
import com.habitloop.challenge.repository.ChallengeParticipantRepository;
import com.habitloop.challenge.repository.ChallengeProgressEventRepository;
import com.habitloop.challenge.repository.ChallengeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    ProgressService service;

    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID challengeId = UUID.randomUUID();
    final UUID participantId = UUID.randomUUID();
    final UUID activityLogId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProgressService(participants, challenges, progressEvents);
    }

    @Test
    void applyActivity_incrementsProgress_forActiveChallenge() {
        ChallengeParticipant p = participant(BigDecimal.ZERO);
        when(participants.findByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of(p));
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId))
                .thenReturn(Optional.of(challenge(new BigDecimal("5"))));
        when(progressEvents.existsByParticipantIdAndActivityLogId(participantId, activityLogId)).thenReturn(false);

        service.applyActivity(tenantId, userId, activityLogId);

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

        service.applyActivity(tenantId, userId, activityLogId);

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

        service.applyActivity(tenantId, userId, activityLogId);

        assertThat(p.getProgress()).isEqualByComparingTo("5");
        assertThat(p.isCompleted()).isTrue();
    }

    private ChallengeParticipant participant(BigDecimal progress) {
        ChallengeParticipant p = new ChallengeParticipant();
        p.setId(participantId);
        p.setTenantId(tenantId);
        p.setChallengeId(challengeId);
        p.setUserId(userId);
        p.setProgress(progress);
        return p;
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
        c.setStartsOn(LocalDate.now().minusDays(1));
        c.setEndsOn(LocalDate.now().plusDays(10));
        return c;
    }
}
