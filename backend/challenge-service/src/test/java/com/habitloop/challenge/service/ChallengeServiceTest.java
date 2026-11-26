package com.habitloop.challenge.service;

import com.habitloop.challenge.dto.ChallengeSummary;
import com.habitloop.challenge.dto.CreateChallengeRequest;
import com.habitloop.challenge.entity.Challenge;
import com.habitloop.challenge.entity.ChallengeParticipant;
import com.habitloop.challenge.entity.ChallengeStatus;
import com.habitloop.challenge.entity.Visibility;
import com.habitloop.challenge.mapper.ChallengeMapper;
import com.habitloop.challenge.repository.ChallengeParticipantRepository;
import com.habitloop.challenge.repository.ChallengeRepository;
import com.habitloop.common.time.TimeZones;
import com.habitloop.common.exception.BusinessException;
import com.habitloop.common.exception.CrossTenantAccessException;
import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock ChallengeRepository challenges;
    @Mock ChallengeParticipantRepository participants;
    @Mock AchievementService achievements;

    ChallengeService service;

    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID challengeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChallengeService(challenges, participants, new ChallengeMapper(), achievements);
        var principal = new AuthUser(userId.toString(), tenantId.toString(), List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_persists_andAutoJoinsCreator() {
        when(challenges.save(any(Challenge.class))).thenAnswer(i -> {
            Challenge c = i.getArgument(0);
            c.setId(challengeId);
            return c;
        });
        when(participants.save(any(ChallengeParticipant.class))).thenAnswer(i -> i.getArgument(0));

        CreateChallengeRequest req = new CreateChallengeRequest("30-day Move", "move daily",
                Visibility.PUBLIC, "DAYS_COMPLETED", new BigDecimal("30"),
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 30));

        ChallengeSummary res = service.create(req);

        assertThat(res.id()).isEqualTo(challengeId.toString());
        assertThat(res.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(res.joined()).isTrue();
        verify(participants).save(any(ChallengeParticipant.class)); // creator auto-joined
        verify(achievements).awardChallengeParticipation(tenantId, userId, challengeId);
    }

    @Test
    void feed_marksOnlyCurrentUsersMemberships() {
        UUID otherId = UUID.randomUUID();
        Challenge joined = challenge(Visibility.PUBLIC);
        Challenge available = challenge(Visibility.PUBLIC);
        available.setId(otherId);
        var pageable = PageRequest.of(0, 10);
        when(challenges.feed(tenantId, userId, Visibility.PUBLIC, pageable))
                .thenReturn(new PageImpl<>(List.of(joined, available), pageable, 2));
        ChallengeParticipant membership = new ChallengeParticipant();
        membership.setChallengeId(challengeId);
        when(participants.findByTenantIdAndUserIdAndChallengeIdIn(tenantId, userId, List.of(challengeId, otherId)))
                .thenReturn(List.of(membership));

        var feed = service.feed(pageable);

        assertThat(feed.content()).extracting(ChallengeSummary::joined).containsExactly(true, false);
    }

    @Test
    void create_rejectsInvertedDateRange() {
        CreateChallengeRequest bad = new CreateChallengeRequest("x", null, Visibility.PUBLIC,
                "DAYS_COMPLETED", BigDecimal.ONE, LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 1));
        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(BusinessException.class);
    }

    @Test
    void join_privateChallenge_isForbidden() {
        Challenge c = challenge(Visibility.PRIVATE);
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.join(challengeId)).isInstanceOf(CrossTenantAccessException.class);
    }

    @Test
    void endedChallengeIsShownAsEndedAndCannotBeJoined() {
        Challenge c = challenge(Visibility.PUBLIC);
        c.setEndsOn(LocalDate.now(TimeZones.INDIA).minusDays(1));
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId)).thenReturn(Optional.of(c));
        assertThat(new ChallengeMapper().toSummary(c, false).status()).isEqualTo(ChallengeStatus.ENDED);
        assertThatThrownBy(() -> service.join(challengeId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("not open");
        verify(participants, never()).save(any(ChallengeParticipant.class));
    }

    @Test
    void challengeIsStillOpenOnItsEndDate() {
        Challenge c = challenge(Visibility.PUBLIC);
        c.setEndsOn(LocalDate.now(TimeZones.INDIA));
        assertThat(new ChallengeMapper().toSummary(c, false).status()).isEqualTo(ChallengeStatus.ACTIVE);
        service.requireOpen(c);
    }

    @Test
    void details_privateChallenge_notParticipant_isHidden() {
        Challenge c = challenge(Visibility.PRIVATE);
        c.setCreatorId(UUID.randomUUID()); // someone else
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId)).thenReturn(Optional.of(c));
        when(participants.findByChallengeIdAndUserId(challengeId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.details(challengeId)).isInstanceOf(ResourceNotFoundException.class);
    }

    private Challenge challenge(Visibility v) {
        Challenge c = new Challenge();
        c.setId(challengeId);
        c.setTenantId(tenantId);
        c.setCreatorId(userId);
        c.setTitle("C");
        c.setVisibility(v);
        c.setMetric("DAYS_COMPLETED");
        c.setTarget(new BigDecimal("30"));
        c.setStartsOn(LocalDate.now(TimeZones.INDIA).minusDays(1));
        c.setEndsOn(LocalDate.now(TimeZones.INDIA).plusDays(10));
        return c;
    }
}
