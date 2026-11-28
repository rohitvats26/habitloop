package com.habitloop.challenge.repository;

import com.habitloop.challenge.entity.ChallengeParticipant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, UUID> {

    Optional<ChallengeParticipant> findByChallengeIdAndUserId(UUID challengeId, UUID userId);

    boolean existsByChallengeIdAndUserId(UUID challengeId, UUID userId);

    List<ChallengeParticipant> findByChallengeIdAndTenantIdOrderByProgressDesc(UUID challengeId, UUID tenantId);

    List<ChallengeParticipant> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ChallengeParticipant> findByTenantIdAndUserIdOrderByIdAsc(UUID tenantId, UUID userId);

    List<ChallengeParticipant> findByTenantIdAndUserIdAndChallengeIdIn(
            UUID tenantId, UUID userId, Collection<UUID> challengeIds);
}
