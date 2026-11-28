package com.habitloop.user.repository;

import com.habitloop.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    List<UserProfile> findByTenantIdAndUserIdIn(UUID tenantId, Collection<UUID> userIds);
}
