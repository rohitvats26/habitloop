package com.habitloop.challenge.service;

import com.habitloop.challenge.client.UserClient;
import com.habitloop.challenge.dto.InvitationResponse;
import com.habitloop.challenge.entity.Challenge;
import com.habitloop.challenge.entity.ChallengeInvitation;
import com.habitloop.challenge.entity.ChallengeParticipant;
import com.habitloop.challenge.entity.InvitationStatus;
import com.habitloop.challenge.entity.Visibility;
import com.habitloop.challenge.mapper.ChallengeMapper;
import com.habitloop.challenge.repository.ChallengeInvitationRepository;
import com.habitloop.challenge.repository.ChallengeParticipantRepository;
import com.habitloop.challenge.repository.ChallengeRepository;
import com.habitloop.common.time.TimeZones;
import com.habitloop.common.exception.CrossTenantAccessException;
import com.habitloop.common.exception.DuplicateResourceException;
import com.habitloop.common.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock ChallengeInvitationRepository invitations;
    @Mock ChallengeRepository challenges;
    @Mock ChallengeParticipantRepository participants;
    @Mock ChallengeService challengeService;
    @Mock UserClient userClient;

    InvitationService service;

    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID inviteeId = UUID.randomUUID();
    final UUID challengeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InvitationService(invitations, challenges, participants, challengeService,
                userClient, new ChallengeMapper());
        var principal = new AuthUser(userId.toString(), tenantId.toString(), List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invite_verifiesUser_andCreatesPendingInvitation() {
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId))
                .thenReturn(Optional.of(challenge(userId)));
        when(invitations.existsByChallengeIdAndInviteeId(challengeId, inviteeId)).thenReturn(false);
        when(participants.existsByChallengeIdAndUserId(challengeId, inviteeId)).thenReturn(false);
        when(invitations.save(any(ChallengeInvitation.class))).thenAnswer(i -> {
            ChallengeInvitation inv = i.getArgument(0);
            inv.setId(UUID.randomUUID());
            return inv;
        });

        InvitationResponse res = service.invite(challengeId, inviteeId);

        verify(userClient).verifyUserInTenant(inviteeId);
        assertThat(res.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(res.inviteeId()).isEqualTo(inviteeId.toString());
    }

    @Test
    void invite_byNonCreator_isForbidden() {
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId))
                .thenReturn(Optional.of(challenge(UUID.randomUUID())));
        assertThatThrownBy(() -> service.invite(challengeId, inviteeId))
                .isInstanceOf(CrossTenantAccessException.class);
    }

    @Test
    void invite_duplicate_throwsConflict() {
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId))
                .thenReturn(Optional.of(challenge(userId)));
        when(invitations.existsByChallengeIdAndInviteeId(challengeId, inviteeId)).thenReturn(true);
        assertThatThrownBy(() -> service.invite(challengeId, inviteeId))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void accept_marksAccepted_andAddsParticipant() {
        ChallengeInvitation inv = new ChallengeInvitation();
        inv.setId(UUID.randomUUID());
        inv.setTenantId(tenantId);
        inv.setChallengeId(challengeId);
        inv.setInviterId(UUID.randomUUID());
        inv.setInviteeId(userId);
        inv.setStatus(InvitationStatus.PENDING);

        when(invitations.findByIdAndTenantId(inv.getId(), tenantId)).thenReturn(Optional.of(inv));
        when(invitations.save(any(ChallengeInvitation.class))).thenAnswer(i -> i.getArgument(0));
        Challenge c = challenge(UUID.randomUUID());
        when(challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId)).thenReturn(Optional.of(c));
        when(participants.existsByChallengeIdAndUserId(challengeId, userId)).thenReturn(false);
        when(challengeService.addParticipant(c, userId)).thenReturn(new ChallengeParticipant());

        InvitationResponse res = service.accept(inv.getId());

        assertThat(res.status()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(challengeService).addParticipant(c, userId);
    }

    private Challenge challenge(UUID creatorId) {
        Challenge c = new Challenge();
        c.setId(challengeId);
        c.setTenantId(tenantId);
        c.setCreatorId(creatorId);
        c.setTitle("C");
        c.setVisibility(Visibility.PRIVATE);
        c.setMetric("DAYS_COMPLETED");
        c.setTarget(new BigDecimal("30"));
        c.setStartsOn(LocalDate.now(TimeZones.INDIA).minusDays(1));
        c.setEndsOn(LocalDate.now(TimeZones.INDIA).plusDays(10));
        return c;
    }
}
