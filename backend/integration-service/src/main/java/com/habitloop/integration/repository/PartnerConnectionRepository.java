package com.habitloop.integration.repository;

import com.habitloop.integration.entity.PartnerConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerConnectionRepository extends JpaRepository<PartnerConnection, UUID> {

    Optional<PartnerConnection> findByIdAndUserIdAndTenantId(UUID id, UUID userId, UUID tenantId);

    List<PartnerConnection> findByUserIdAndTenantId(UUID userId, UUID tenantId);
}
