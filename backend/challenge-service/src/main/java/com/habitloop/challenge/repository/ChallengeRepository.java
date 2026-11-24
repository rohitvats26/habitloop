package com.habitloop.challenge.repository;

import com.habitloop.challenge.entity.Challenge;
import com.habitloop.challenge.entity.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    Optional<Challenge> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    /** Feed: public challenges in the tenant plus the caller's own, newest first. */
    @Query("""
            select c from Challenge c
            where c.tenantId = :tenantId and c.deletedAt is null
              and (c.visibility = :publicVisibility or c.creatorId = :userId)
            order by c.createdAt desc
            """)
    Page<Challenge> feed(@Param("tenantId") UUID tenantId,
                         @Param("userId") UUID userId,
                         @Param("publicVisibility") Visibility publicVisibility,
                         Pageable pageable);
}
