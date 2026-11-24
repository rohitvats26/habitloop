package com.habitloop.challenge.repository;

import com.habitloop.challenge.entity.ChallengeInvitation;
import com.habitloop.challenge.entity.InvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChallengeInvitationRepository extends JpaRepository<ChallengeInvitation, UUID> {

    boolean existsByChallengeIdAndInviteeId(UUID challengeId, UUID inviteeId);

    Optional<ChallengeInvitation> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ChallengeInvitation> findByTenantIdAndInviteeIdAndStatus(
            UUID tenantId, UUID inviteeId, InvitationStatus status, Pageable pageable);
}
