package com.habitloop.challenge.service;

import com.habitloop.challenge.client.UserClient;
import com.habitloop.challenge.dto.InvitationResponse;
import com.habitloop.challenge.entity.Challenge;
import com.habitloop.challenge.entity.ChallengeInvitation;
import com.habitloop.challenge.entity.InvitationStatus;
import com.habitloop.challenge.mapper.ChallengeMapper;
import com.habitloop.challenge.repository.ChallengeInvitationRepository;
import com.habitloop.challenge.repository.ChallengeParticipantRepository;
import com.habitloop.challenge.repository.ChallengeRepository;
import com.habitloop.common.exception.BusinessException;
import com.habitloop.common.exception.CrossTenantAccessException;
import com.habitloop.common.exception.DuplicateResourceException;
import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InvitationService {

    private final ChallengeInvitationRepository invitations;
    private final ChallengeRepository challenges;
    private final ChallengeParticipantRepository participants;
    private final ChallengeService challengeService;
    private final UserClient userClient;
    private final ChallengeMapper mapper;

    public InvitationService(ChallengeInvitationRepository invitations, ChallengeRepository challenges,
                             ChallengeParticipantRepository participants, ChallengeService challengeService,
                             UserClient userClient, ChallengeMapper mapper) {
        this.invitations = invitations;
        this.challenges = challenges;
        this.participants = participants;
        this.challengeService = challengeService;
        this.userClient = userClient;
        this.mapper = mapper;
    }

    @Transactional
    public InvitationResponse invite(UUID challengeId, UUID inviteeId) {
        Challenge c = challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Challenge", challengeId));
        if (!c.getCreatorId().equals(userId())) {
            throw new CrossTenantAccessException("Only the challenge creator can invite");
        }
        challengeService.requireOpen(c);
        if (inviteeId.equals(userId())) {
            throw new BusinessException("Cannot invite yourself");
        }
        // Verify the invitee exists in the caller's tenant (circuit-broken, times out safely).
        userClient.verifyUserInTenant(inviteeId);

        if (invitations.existsByChallengeIdAndInviteeId(challengeId, inviteeId)
                || participants.existsByChallengeIdAndUserId(challengeId, inviteeId)) {
            throw new DuplicateResourceException("User already invited or participating");
        }

        ChallengeInvitation inv = new ChallengeInvitation();
        inv.setTenantId(tenantId());
        inv.setChallengeId(challengeId);
        inv.setInviterId(userId());
        inv.setInviteeId(inviteeId);
        inv.setStatus(InvitationStatus.PENDING);
        inv = invitations.save(inv);
        return mapper.toInvitationResponse(inv);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> myPendingInvitations(Pageable pageable) {
        UUID tenant = tenantId();
        var page = invitations.findByTenantIdAndInviteeIdAndStatus(
                tenant, userId(), InvitationStatus.PENDING, pageable);
        Set<UUID> challengeIds = page.getContent().stream()
                .map(ChallengeInvitation::getChallengeId).collect(Collectors.toSet());
        Map<UUID, String> titles = challengeIds.isEmpty() ? Map.of()
                : challenges.findAllById(challengeIds).stream()
                        .filter(c -> tenant.equals(c.getTenantId()) && c.getDeletedAt() == null)
                        .collect(Collectors.toMap(Challenge::getId, Challenge::getTitle));
        Map<String, String> names = userClient.displayNames(page.getContent().stream()
                .map(ChallengeInvitation::getInviterId).collect(Collectors.toSet()));
        return PageResponse.of(page.map(inv -> mapper.toInvitationResponse(inv,
                titles.get(inv.getChallengeId()), names.get(inv.getInviterId().toString()))));
    }

    @Transactional
    public InvitationResponse accept(UUID invitationId) {
        ChallengeInvitation inv = requirePendingForMe(invitationId);
        Challenge c = challenges.findByIdAndTenantIdAndDeletedAtIsNull(inv.getChallengeId(), tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Challenge", inv.getChallengeId()));
        boolean alreadyJoined = participants.existsByChallengeIdAndUserId(c.getId(), userId());
        if (!alreadyJoined) {
            challengeService.requireOpen(c);
        }
        inv.setStatus(InvitationStatus.ACCEPTED);
        inv.setRespondedAt(OffsetDateTime.now());
        invitations.save(inv);

        if (!alreadyJoined) {
            challengeService.addParticipant(c, userId());
        }
        return mapper.toInvitationResponse(inv);
    }

    @Transactional
    public InvitationResponse reject(UUID invitationId) {
        ChallengeInvitation inv = requirePendingForMe(invitationId);
        inv.setStatus(InvitationStatus.REJECTED);
        inv.setRespondedAt(OffsetDateTime.now());
        invitations.save(inv);
        return mapper.toInvitationResponse(inv);
    }

    private ChallengeInvitation requirePendingForMe(UUID invitationId) {
        ChallengeInvitation inv = invitations.findByIdAndTenantId(invitationId, tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
        if (!inv.getInviteeId().equals(userId())) {
            throw new CrossTenantAccessException("Not your invitation");
        }
        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessException("Invitation already " + inv.getStatus());
        }
        return inv;
    }

    private UUID tenantId() {
        return UUID.fromString(SecurityUtils.currentTenantId());
    }

    private UUID userId() {
        return UUID.fromString(SecurityUtils.currentUserId());
    }
}
