package com.habitloop.activity.repository;

import com.habitloop.activity.entity.Habit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HabitRepository extends JpaRepository<Habit, UUID> {

    Optional<Habit> findByIdAndUserIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID userId, UUID tenantId);

    Page<Habit> findByUserIdAndTenantIdAndDeletedAtIsNull(UUID userId, UUID tenantId, Pageable pageable);

    List<Habit> findByActiveFalseAndPausedUntilLessThanEqualAndDeletedAtIsNull(LocalDate date);
}
