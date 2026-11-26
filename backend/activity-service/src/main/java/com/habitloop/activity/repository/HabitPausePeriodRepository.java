package com.habitloop.activity.repository;

import com.habitloop.activity.entity.HabitPausePeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HabitPausePeriodRepository extends JpaRepository<HabitPausePeriod, UUID> {

    List<HabitPausePeriod> findByHabitIdAndTenantIdAndStartDateLessThanAndResumeDateGreaterThan(
            UUID habitId, UUID tenantId, LocalDate toExclusive, LocalDate fromInclusive);

    Optional<HabitPausePeriod> findFirstByHabitIdAndTenantIdAndResumedAtIsNullOrderByStartDateDesc(
            UUID habitId, UUID tenantId);
}
