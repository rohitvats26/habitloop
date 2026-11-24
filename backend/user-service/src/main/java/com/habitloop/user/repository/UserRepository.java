package com.habitloop.user.repository;

import com.habitloop.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);

    List<User> findByEmail(String email);

    /** Tenant-scoped lookup — the only way callers fetch a user by id, enforcing isolation. */
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);
}
