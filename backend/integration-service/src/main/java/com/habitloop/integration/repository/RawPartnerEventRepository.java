package com.habitloop.integration.repository;

import com.habitloop.integration.entity.RawPartnerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RawPartnerEventRepository extends JpaRepository<RawPartnerEvent, UUID> {

    boolean existsByConnectionIdAndExternalEventId(UUID connectionId, String externalEventId);
}
