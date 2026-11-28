package com.habitloop.integration.repository;

import com.habitloop.integration.entity.PartnerConnection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerConnectionRepository extends JpaRepository<PartnerConnection, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PartnerConnection> findForUpdateById(UUID id);

    Optional<PartnerConnection> findByIdAndUserIdAndTenantId(UUID id, UUID userId, UUID tenantId);

    List<PartnerConnection> findByUserIdAndTenantId(UUID userId, UUID tenantId);
}
