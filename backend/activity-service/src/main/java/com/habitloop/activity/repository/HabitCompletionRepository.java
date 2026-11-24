package com.habitloop.activity.repository;

import com.habitloop.activity.entity.HabitCompletion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, UUID> {

    boolean existsByHabitIdAndCompletionDate(UUID habitId, LocalDate completionDate);

    Page<HabitCompletion> findByHabitIdAndTenantId(UUID habitId, UUID tenantId, Pageable pageable);
}
