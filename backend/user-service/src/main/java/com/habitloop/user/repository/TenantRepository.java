package com.habitloop.user.repository;

import com.habitloop.user.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);
}
